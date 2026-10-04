#!/usr/bin/env python3
#
# Copyright 2026 Gua
#
# SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
# Please see LICENSE files in the repository root for full details.

"""Checks the translations of the languages Gua ships (pt-BR, es and fr besides English).

1. Every string and plural in a module's values/temporary.xml (Gua's own strings) has a pt-rBR,
   es and fr entry that differs from the English.
2. Every string or plural that production Kotlin references has a pt-rBR, es and fr entry that
   differs from the English.
3. Every locale in app/src/main/res/xml/locales_config.xml is a BCP 47 tag, and the list matches
   the locale filters in plugins/src/main/kotlin/extension/locales.kt.

`<locale>:<key>` pairs listed in tools/scripts/translations-baseline.txt are allowed to fail 1 or 2.
The script also fails on baseline entries that are no longer needed.

Usage: check-translations.py [--print-gaps]
"""

import argparse
import glob
import os
import re
import sys
import xml.etree.ElementTree as ET

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
LOCALES = ["pt-rBR", "es", "fr"]
BASELINE = os.path.join(ROOT, "tools", "scripts", "translations-baseline.txt")
LOCALES_CONFIG = os.path.join(ROOT, "app", "src", "main", "res", "xml", "locales_config.xml")
LOCALE_FILTERS = os.path.join(ROOT, "plugins", "src", "main", "kotlin", "extension", "locales.kt")
SKIPPED_DIRS = {"build", ".git", ".gradle", "enterprise", "node_modules"}
# Slash commands are a debug-only feature.
SKIPPED_MODULES = {os.path.join("libraries", "slashcommands", "impl")}
ANDROID_NS = "{http://schemas.android.com/apk/res/android}"
KOTLIN_REFERENCE = re.compile(r"\b(?:\w*R\.(?:string|plurals)|CommonStrings|CommonPlurals)\.([a-z0-9_]+)\b")
BCP47 = re.compile(r"^[a-z]{2,3}(-[A-Z][a-z]{3})?(-([A-Z]{2}|[0-9]{3}))?$")


def source_dirs():
    for dirpath, dirnames, filenames in os.walk(ROOT):
        # The root tests/ folder holds test tooling, not app code.
        dirnames[:] = [
            d for d in dirnames
            if d not in SKIPPED_DIRS
            and not (dirpath == ROOT and d == "tests")
            and os.path.relpath(os.path.join(dirpath, d), ROOT) not in SKIPPED_MODULES
        ]
        yield dirpath, filenames


def text_of(element):
    if element.tag == "plurals":
        return tuple(sorted((item.get("quantity"), text_of(item)) for item in element.findall("item")))
    raw = "".join(element.itertext()).strip()
    if len(raw) >= 2 and raw.startswith('"') and raw.endswith('"'):
        raw = raw[1:-1]
    return re.sub(r"\s+", " ", raw.replace("\\'", "'").replace('\\"', '"'))


def read_resources(path):
    try:
        root = ET.parse(path).getroot()
    except ET.ParseError as error:
        sys.exit(f"{os.path.relpath(path, ROOT)}: invalid XML: {error}")
    return [element for element in root if element.tag in ("string", "plurals")]


def load_resources():
    """Returns (english, translated, gua_keys): English text by key, translated text by locale and
    key, and Gua's own keys by module."""
    english, untranslatable, gua_keys = {}, set(), {}
    translated = {locale: {} for locale in LOCALES}
    for dirpath, _ in source_dirs():
        if not dirpath.endswith(os.path.join("src", "main", "res")):
            continue
        module = os.path.relpath(dirpath[: -len(os.path.join("src", "main", "res"))], ROOT)
        for path in glob.glob(os.path.join(dirpath, "values", "*.xml")):
            for element in read_resources(path):
                key = element.get("name")
                if element.get("translatable") == "false":
                    untranslatable.add(key)
                    continue
                english.setdefault(key, text_of(element))
                if os.path.basename(path) == "temporary.xml":
                    gua_keys.setdefault(module, []).append(key)
        for locale in LOCALES:
            for path in glob.glob(os.path.join(dirpath, f"values-{locale}", "*.xml")):
                for element in read_resources(path):
                    translated[locale][element.get("name")] = text_of(element)
    for key in untranslatable:
        english.pop(key, None)
    return english, translated, gua_keys


def referenced_keys():
    keys = set()
    for dirpath, filenames in source_dirs():
        if f"{os.sep}src{os.sep}main{os.sep}" not in dirpath + os.sep:
            continue
        for filename in filenames:
            if filename.endswith(".kt"):
                with open(os.path.join(dirpath, filename), encoding="utf-8") as source:
                    keys.update(KOTLIN_REFERENCE.findall(source.read()))
    return keys


def check_locales_config():
    errors = []
    tags = [element.get(f"{ANDROID_NS}name") or "" for element in ET.parse(LOCALES_CONFIG).getroot()]
    for tag in tags:
        if not BCP47.match(tag):
            errors.append(f"locales_config.xml: '{tag}' is not a BCP 47 language tag")
    with open(LOCALE_FILTERS, encoding="utf-8") as source:
        filters = re.findall(r'"([A-Za-z-]+)"', source.read())
    expected = sorted(locale_filter.replace("-r", "-") for locale_filter in filters)
    if sorted(tags) != expected:
        errors.append(f"locales_config.xml lists {sorted(tags)}, the locale filters ship {expected}")
    return errors


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--print-gaps", action="store_true", help="print every untranslated pair, baseline included")
    args = parser.parse_args()

    english, translated, gua_keys = load_resources()

    def untranslated(key, locale):
        value = translated[locale].get(key)
        return value is None or value == english[key]

    gaps = {}
    for module, keys in sorted(gua_keys.items()):
        for key in keys:
            for locale in LOCALES:
                if untranslated(key, locale):
                    gaps[f"{locale}:{key}"] = f"{module}: Gua string '{key}' has no {locale} translation"
    all_gua_keys = {key for keys in gua_keys.values() for key in keys}
    for key in sorted(referenced_keys()):
        if key not in english or key in all_gua_keys:
            continue
        for locale in LOCALES:
            if untranslated(key, locale):
                gaps[f"{locale}:{key}"] = f"'{key}' is used in code and has no {locale} translation"

    if args.print_gaps:
        print("\n".join(sorted(gaps)))
        return 0

    with open(BASELINE, encoding="utf-8") as source:
        baseline = {line.strip() for line in source if line.strip() and not line.startswith("#")}
    errors = check_locales_config()
    errors += [gaps[pair] for pair in sorted(set(gaps) - baseline)]
    errors += [f"'{pair}' is translated now, remove it from {os.path.relpath(BASELINE, ROOT)}" for pair in sorted(baseline - set(gaps))]

    for error in errors:
        print(f"error: {error}")
    if errors:
        print(f"{len(errors)} translation problem(s)")
        return 1
    print(f"Translations OK ({len(baseline)} baseline entries)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
