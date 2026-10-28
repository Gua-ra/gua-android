/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver.verification

import java.security.MessageDigest

/**
 * RFC 6962 hashing for the resolver's transparency log: `leaf = SHA-256(0x00 || data)`,
 * `node = SHA-256(0x01 || left || right)`, the empty tree is SHA-256 of nothing. Hashes travel as
 * lowercase hex. Port of the resolver's `MerkleTree`.
 */
internal object MerkleTree {
    private const val LEAF_PREFIX: Byte = 0x00
    private const val NODE_PREFIX: Byte = 0x01

    fun sha256Hex(data: ByteArray): String = Hex.encode(sha256().digest(data))

    fun leafHash(data: ByteArray): String {
        val digest = sha256()
        digest.update(LEAF_PREFIX)
        digest.update(data)
        return Hex.encode(digest.digest())
    }

    /** The Merkle Tree Hash over leaf hashes, or null when one of them is not hex. */
    fun root(leafHashes: List<String>): String? {
        if (leafHashes.isEmpty()) return Hex.encode(sha256().digest())
        val nodes = leafHashes.map { Hex.decode(it) ?: return null }
        return Hex.encode(mth(nodes))
    }

    /**
     * Whether the tree of [second] leaves with [newRoot] is an append-only extension of the tree of
     * [first] leaves with [oldRoot], per RFC 6962 section 2.1.2. A proof that does not decode fails.
     */
    fun verifyConsistency(first: Long, second: Long, oldRoot: String, newRoot: String, proof: List<String>): Boolean {
        if (first == 0L) return second >= 0L
        if (first == second) return proof.isEmpty() && oldRoot == newRoot
        if (first > second || proof.isEmpty()) return false

        val path = proof.map { Hex.decode(it) ?: return false }
        var index = 0
        val seed = if (first.countOneBits() == 1) Hex.decode(oldRoot) ?: return false else path[index++]
        var toOldRoot = seed
        var toNewRoot = seed
        var fn = first - 1
        var sn = second - 1
        while (fn and 1L == 1L) {
            fn = fn shr 1
            sn = sn shr 1
        }
        while (index < path.size) {
            val node = path[index++]
            if (fn and 1L == 1L || fn == sn) {
                toOldRoot = nodeHash(node, toOldRoot)
                toNewRoot = nodeHash(node, toNewRoot)
                while (fn and 1L == 0L && fn != 0L) {
                    fn = fn shr 1
                    sn = sn shr 1
                }
            } else {
                toNewRoot = nodeHash(toNewRoot, node)
            }
            fn = fn shr 1
            sn = sn shr 1
        }
        return Hex.encode(toOldRoot) == oldRoot && Hex.encode(toNewRoot) == newRoot && sn == 0L
    }

    private fun mth(nodes: List<ByteArray>): ByteArray {
        if (nodes.size == 1) return nodes[0]
        val split = largestPowerOfTwoBelow(nodes.size)
        return nodeHash(mth(nodes.subList(0, split)), mth(nodes.subList(split, nodes.size)))
    }

    private fun largestPowerOfTwoBelow(n: Int): Int {
        var k = 1
        while (k shl 1 < n) k = k shl 1
        return k
    }

    private fun nodeHash(left: ByteArray, right: ByteArray): ByteArray {
        val digest = sha256()
        digest.update(NODE_PREFIX)
        digest.update(left)
        digest.update(right)
        return digest.digest()
    }

    private fun sha256(): MessageDigest = MessageDigest.getInstance("SHA-256")
}
