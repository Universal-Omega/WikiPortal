import glob
import os
import re
import lxml.etree as ET  # builtin library doesn't preserve comments


COMPOSE_LOCALE_GLOB = "**/composeResources/values-*"
COMPOSE_SOURCE_GLOB = "**/composeResources/values/strings.xml"

LOCALES_CONFIG_PATH = "androidApp/src/main/res/xml/locales_config.xml"
BASE_LOCALE = "en"

LEGACY_DIR = re.compile(r"^values-(?P<lang>[a-z]{2,3})(?:-r(?P<region>[A-Z]{2}|[0-9]{3}))?$")
BCP47_DIR = re.compile(
    r"^values-b\+(?P<lang>[a-z]{2,3})(?:\+(?P<script>[A-Z][a-z]{3}))?(?:\+(?P<region>[A-Z]{2}|[0-9]{3}))?$"
)
LEGACY_LANGUAGES = {"in": "id", "iw": "he", "ji": "yi"}


def locale_folders():
    for folder in glob.glob(COMPOSE_LOCALE_GLOB, recursive=True):
        if os.path.isdir(folder) and "build" not in folder.split(os.sep):
            yield folder


def compose_dir_to_tag(dir_name):
    # values-fr-rFR and values-b+fr+FR both become fr-FR. Anything else returns None.
    match = BCP47_DIR.match(dir_name)
    if match:
        parts = (match.group("lang"), match.group("script"), match.group("region"))
        return "-".join(part for part in parts if part)
    match = LEGACY_DIR.match(dir_name)
    if match:
        region = match.group("region")
        return f"{match.group('lang')}-{region}" if region else match.group("lang")
    return None


def warn_legacy_language(folder, tag):
    # Weblate writes in, iw and ji for Android but Compose Multiplatform matches id, he and yi.
    modern = LEGACY_LANGUAGES.get(tag.split("-")[0])
    if modern:
        print(f"::warning::{folder} uses a legacy language code, rename it to use {modern}")


def warn_missing_script_fallback(folder):
    # A script folder needs a script less folder next to it or the locale can fail to resolve.
    match = BCP47_DIR.match(os.path.basename(folder))
    if not match or not match.group("script"):
        return
    parent = os.path.dirname(folder)
    lang = match.group("lang")
    fallback = os.path.join(parent, f"values-b+{lang}")
    if not os.path.isdir(fallback) and not os.path.isdir(os.path.join(parent, f"values-{lang}")):
        print(f"::warning::{folder} has no script less fallback at {fallback}")


def get_source_order(source_path):
    # Returns the resource names in the order they appear in the given strings.xml.
    if not os.path.exists(source_path):
        return []
    tree = ET.parse(source_path)
    order = []
    for el in tree.getroot():
        name = el.attrib.get('name')
        if name:
            order.append(name)
    return order


def sort_to_source_order(file_path, order):
    # Reorders string, string-array and plurals elements to match the source file.
    # Anything not present in the source keeps its original relative position at the end.
    if not order:
        return
    tree = ET.parse(file_path)
    root = tree.getroot()
    index = {name: i for i, name in enumerate(order)}
    children = list(root)
    matched = [c for c in children if c.attrib.get('name') in index]
    unmatched = [c for c in children if c.attrib.get('name') not in index]
    matched.sort(key=lambda c: index[c.attrib['name']])
    new_order = matched + unmatched
    if new_order == children:
        return
    for c in children:
        root.remove(c)
    for c in new_order:
        root.append(c)
    root.text = "\n    "
    for i, c in enumerate(new_order):
        c.tail = "\n" if i == len(new_order) - 1 else "\n    "
    with open(file_path, 'wb') as fp:
        fp.write(b'<?xml version="1.0" encoding="utf-8"?>\n')
        tree.write(fp, encoding="utf-8", method="xml", pretty_print=True, xml_declaration=False)


def write_locales_config(path, tags):
    # Writes an Android locale-config XML listing every supported locale tag.
    ns = "http://schemas.android.com/apk/res/android"
    root = ET.Element("locale-config", nsmap={"android": ns})
    for tag in tags:
        locale = ET.SubElement(root, "locale")
        locale.set(f"{{{ns}}}name", tag)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    tree = ET.ElementTree(root)
    with open(path, 'wb') as fp:
        fp.write(b'<?xml version="1.0" encoding="utf-8"?>\n')
        tree.write(fp, encoding="utf-8", method="xml", pretty_print=True, xml_declaration=False)


# Collect every locale folder from every composeResources module.
locale_tags = {BASE_LOCALE}
for folder in locale_folders():
    tag = compose_dir_to_tag(os.path.basename(folder))
    if tag is None:
        print(f"::warning::Skipping unrecognized folder {folder}")
        continue
    locale_tags.add(tag)
    warn_missing_script_fallback(folder)
    warn_legacy_language(folder, tag)

write_locales_config(LOCALES_CONFIG_PATH, sorted(locale_tags))

# Sort strings in every locale file to match each module's source key order.
for source_file in glob.glob(COMPOSE_SOURCE_GLOB, recursive=True):
    compose_dir = os.path.dirname(os.path.dirname(source_file))  # .../composeResources
    order = get_source_order(source_file)
    for file in glob.glob(os.path.join(compose_dir, "values-*", "strings.xml")):
        sort_to_source_order(file, order)
