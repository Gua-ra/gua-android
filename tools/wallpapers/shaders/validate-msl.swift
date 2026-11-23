//
// Copyright 2026 Gua
//
// SPDX-License-Identifier: AGPL-3.0-only
// Please see LICENSE files in the repository root for full details.
//

// Compiles every generated .msl at runtime (as the iOS app does), renders each golden case offscreen
// at twice the golden's size (the goldens are stored 2x box-downsampled) and compares.
// Usage: swift tools/wallpapers/shaders/validate-msl.swift <folder with .msl files> [goldens folder] [--out <folder for the Metal renders>]
// Exits non-zero when a case is over the limits (harness.mjs: mean 1.5/255, max 10/255, and the
// allowances there) or a shader fails to compile.

import CoreGraphics
import Foundation
import ImageIO
import Metal
import UniformTypeIdentifiers

// Must match GuaWallpaperUniforms in the .msl files.
struct Uniforms {
    var res: SIMD2<Float>
    var tilt: SIMD2<Float>
    var origin: SIMD2<Float>
    var time: Float
    var dark: Float
    var pulse: Float
    var pad: Float = 0
}

// Same limits and allowances as harness.mjs.
let limitMean = 1.5
let limitMax = 10.0
let allowances: [String: (mean: Double, max: Double)] = ["molten-pearl": (0.1, 16), "ipe": (0.1, 16)]

var positional: [String] = []
var outDir: URL?
var argIndex = 1
while argIndex < CommandLine.arguments.count {
    let arg = CommandLine.arguments[argIndex]
    if arg == "--out", argIndex + 1 < CommandLine.arguments.count {
        outDir = URL(fileURLWithPath: CommandLine.arguments[argIndex + 1])
        argIndex += 2
    } else {
        positional.append(arg)
        argIndex += 1
    }
}
guard let mslPath = positional.first else {
    print("usage: swift validate-msl.swift <folder with .msl files> [goldens folder] [--out <folder>]")
    exit(2)
}
let scriptDir = URL(fileURLWithPath: #filePath).deletingLastPathComponent()
let mslDir = URL(fileURLWithPath: mslPath)
let goldenDir = positional.count > 1 ? URL(fileURLWithPath: positional[1]) : scriptDir.appendingPathComponent("goldens")
if let outDir { try FileManager.default.createDirectory(at: outDir, withIntermediateDirectories: true) }

guard MemoryLayout<Uniforms>.stride == 40 else { fatalError("Uniforms must be 40 bytes") }
guard let device = MTLCreateSystemDefaultDevice(), let queue = device.makeCommandQueue() else { fatalError("no Metal device") }

let options = MTLCompileOptions()
options.mathMode = .safe
options.mathFloatingPointFunctions = .precise

// Raw 8-bit RGBA of a PNG, with no colour management.
func loadRGBA(_ url: URL) -> (width: Int, height: Int, pixels: [UInt8])? {
    guard let source = CGImageSourceCreateWithURL(url as CFURL, nil),
          let image = CGImageSourceCreateImageAtIndex(source, 0, nil) else { return nil }
    let width = image.width, height = image.height
    var pixels = [UInt8](repeating: 0, count: width * height * 4)
    let space = image.colorSpace ?? CGColorSpaceCreateDeviceRGB()
    let drawn = pixels.withUnsafeMutableBytes { buffer -> Bool in
        guard let context = CGContext(data: buffer.baseAddress, width: width, height: height, bitsPerComponent: 8,
                                      bytesPerRow: width * 4, space: space,
                                      bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue) else { return false }
        context.interpolationQuality = .none
        context.draw(image, in: CGRect(x: 0, y: 0, width: width, height: height))
        return true
    }
    return drawn ? (width, height, pixels) : nil
}

func savePNG(_ pixels: [UInt8], width: Int, height: Int, to url: URL) {
    var copy = pixels
    copy.withUnsafeMutableBytes { buffer in
        guard let context = CGContext(data: buffer.baseAddress, width: width, height: height, bitsPerComponent: 8,
                                      bytesPerRow: width * 4, space: CGColorSpaceCreateDeviceRGB(),
                                      bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue),
              let image = context.makeImage(),
              let destination = CGImageDestinationCreateWithURL(url as CFURL, UTType.png.identifier as CFString, 1, nil) else { return }
        CGImageDestinationAddImage(destination, image, nil)
        CGImageDestinationFinalize(destination)
    }
}

// 2x box downsample, each channel rounded half up, exactly as downsample2x in harness.mjs.
func downsample2x(_ pixels: [UInt8], width: Int, height: Int) -> [UInt8] {
    let outWidth = width / 2, outHeight = height / 2
    var out = [UInt8](repeating: 255, count: outWidth * outHeight * 4)
    for y in 0..<outHeight {
        for x in 0..<outWidth {
            for channel in 0..<3 {
                var sum = 0
                for (dx, dy) in [(0, 0), (1, 0), (0, 1), (1, 1)] {
                    sum += Int(pixels[((2 * y + dy) * width + 2 * x + dx) * 4 + channel])
                }
                out[(y * outWidth + x) * 4 + channel] = UInt8((sum + 2) >> 2)
            }
        }
    }
    return out
}

func compare(_ a: [UInt8], _ b: [UInt8]) -> (mean: Double, max: Double) {
    var sum = 0.0, maxDiff = 0.0, count = 0
    for i in stride(from: 0, to: a.count, by: 4) {
        for channel in 0..<3 {
            let v = Double(abs(Int(a[i + channel]) - Int(b[i + channel])))
            sum += v
            maxDiff = max(maxDiff, v)
            count += 1
        }
    }
    return (sum / Double(count), maxDiff)
}

let namePattern = try NSRegularExpression(pattern: #"^(.+)-(light|dark)-t(\d+)-p(\d+)\.png$"#)
let goldens = try FileManager.default.contentsOfDirectory(atPath: goldenDir.path).filter { $0.hasSuffix(".png") }.sorted()
var pipelines: [String: MTLRenderPipelineState] = [:]
var failed = false
var worst: [String: (mean: Double, max: Double)] = [:]

for name in goldens {
    guard let match = namePattern.firstMatch(in: name, range: NSRange(name.startIndex..., in: name)) else { continue }
    func group(_ i: Int) -> String { String(name[Range(match.range(at: i), in: name)!]) }
    let id = group(1)
    let dark = group(2) == "dark"
    let time = Float(group(3))!
    let pulse = Float(group(4))! / 100

    if pipelines[id] == nil {
        let source = try String(contentsOf: mslDir.appendingPathComponent("\(id).msl"), encoding: .utf8)
        do {
            let library = try device.makeLibrary(source: source, options: options)
            let descriptor = MTLRenderPipelineDescriptor()
            descriptor.vertexFunction = library.makeFunction(name: "gua_wallpaper_vertex")
            descriptor.fragmentFunction = library.makeFunction(name: "gua_wallpaper_fragment")
            descriptor.colorAttachments[0].pixelFormat = .rgba8Unorm
            pipelines[id] = try device.makeRenderPipelineState(descriptor: descriptor)
        } catch {
            print("\(id).msl: \(error)")
            failed = true
            continue
        }
    }
    guard let pipeline = pipelines[id], let golden = loadRGBA(goldenDir.appendingPathComponent(name)) else { continue }

    let width = golden.width * 2, height = golden.height * 2
    let textureDescriptor = MTLTextureDescriptor.texture2DDescriptor(pixelFormat: .rgba8Unorm, width: width,
                                                                     height: height, mipmapped: false)
    textureDescriptor.usage = .renderTarget
    textureDescriptor.storageMode = .shared
    let texture = device.makeTexture(descriptor: textureDescriptor)!
    let pass = MTLRenderPassDescriptor()
    pass.colorAttachments[0].texture = texture
    pass.colorAttachments[0].loadAction = .clear
    pass.colorAttachments[0].storeAction = .store
    var uniforms = Uniforms(res: SIMD2(Float(width), Float(height)), tilt: .zero, origin: .zero,
                            time: time, dark: dark ? 1 : 0, pulse: pulse)
    let commands = queue.makeCommandBuffer()!
    let encoder = commands.makeRenderCommandEncoder(descriptor: pass)!
    encoder.setRenderPipelineState(pipeline)
    encoder.setFragmentBytes(&uniforms, length: MemoryLayout<Uniforms>.stride, index: 0)
    encoder.drawPrimitives(type: .triangle, vertexStart: 0, vertexCount: 3)
    encoder.endEncoding()
    commands.commit()
    commands.waitUntilCompleted()

    var rendered = [UInt8](repeating: 0, count: width * height * 4)
    texture.getBytes(&rendered, bytesPerRow: width * 4,
                     from: MTLRegionMake2D(0, 0, width, height), mipmapLevel: 0)
    if let outDir { savePNG(rendered, width: width, height: height, to: outDir.appendingPathComponent(name)) }

    let result = compare(downsample2x(rendered, width: width, height: height), golden.pixels)
    let limit = allowances[id] ?? (limitMean, limitMax)
    let over = result.mean > limit.mean || result.max > limit.max
    failed = failed || over
    let previous = worst[id] ?? (0, 0)
    worst[id] = (max(previous.mean, result.mean), max(previous.max, result.max))
    let label = name.padding(toLength: 34, withPad: " ", startingAt: 0)
    print("\(over ? "OVER" : "ok  ") \(label) " + String(format: "mean %.3f  max %5.2f", result.mean, result.max))
}

print("\nworst case per shader (8-bit levels, after 2x box downsample):")
for (id, value) in worst.sorted(by: { $0.key < $1.key }) {
    print(id.padding(toLength: 18, withPad: " ", startingAt: 0) + String(format: " mean %.3f  max %5.2f", value.mean, value.max))
}
exit(failed ? 1 : 0)
