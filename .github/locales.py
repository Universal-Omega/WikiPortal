import glob
import os
import lxml.etree as ET  # builtin library doesn't preserve comments


COMPOSE_LOCALE_GLOB = "**/composeResources/values-*"
COMPOSE_SOURCE_GLOB = "**/composeResources/values/strings.xml"

LOCALES_CONFIG_PATH = "androidApp/src/main/res/xml/locales_config.xml"
BASE_LOCALE = "en"


def compose_dir_to_iso(dir_name):
    # values-fr becomes fr, values-fr-rFR becomes fr-FR.
    code = dir_name[len("values-"):]
    parts = code.split("-")
    if len(parts) == 2 and parts[1].startswith("r"):
        return f"{parts[0]}-{parts[1][1:]}"
    return code


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


def write_locales_config(path, isos):
    # Writes an Android locale-config XML listing every supported locale tag.
    ns = "http://schemas.android.com/apk/res/android"
    root = ET.Element("locale-config", nsmap={"android": ns})
    for iso in isos:
        locale = ET.SubElement(root, "locale")
        locale.set(f"{{{ns}}}name", iso)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    tree = ET.ElementTree(root)
    with open(path, 'wb') as fp:
        fp.write(b'<?xml version="1.0" encoding="utf-8"?>\n')
        tree.write(fp, encoding="utf-8", method="xml", pretty_print=True, xml_declaration=False)


# Collect every locale folder from every composeResources module.
locale_isos = {BASE_LOCALE}
for folder in glob.glob(COMPOSE_LOCALE_GLOB, recursive=True):
    if os.path.basename(folder) == "values":
        continue
    locale_isos.add(compose_dir_to_iso(os.path.basename(folder)))

write_locales_config(LOCALES_CONFIG_PATH, sorted(locale_isos))

# Sort strings in every locale file to match each module's source key order.
for source_file in glob.glob(COMPOSE_SOURCE_GLOB, recursive=True):
    compose_dir = os.path.dirname(os.path.dirname(source_file))  # .../composeResources
    order = get_source_order(source_file)
    for file in glob.glob(os.path.join(compose_dir, "values-*", "strings.xml")):
        sort_to_source_order(file, order)
