import re
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path

RES = Path(__file__).resolve().parent.parent / "app" / "src" / "main" / "res"
FORMAT = re.compile(r"%(?:\d+\$)?[-+0 #]*(?:\d+)?(?:\.\d+)?[a-zA-Z%]")


class LocalizationTests(unittest.TestCase):
    def test_english_and_chinese_have_complete_matching_resources_and_format_arguments(self):
        english = {node.attrib["name"]: node for node in ET.parse(RES / "values" / "strings.xml").getroot()}
        chinese = {node.attrib["name"]: node for node in ET.parse(RES / "values-zh" / "strings.xml").getroot()}
        self.assertEqual(set(english), set(chinese))
        for name, en in english.items():
            zh = chinese[name]
            self.assertEqual(en.tag, zh.tag, name)
            en_texts = [en.text] if en.tag == "string" else [item.text for item in en]
            zh_texts = [zh.text] if zh.tag == "string" else [item.text for item in zh]
            self.assertEqual(len(en_texts), len(zh_texts), name)
            for en_text, zh_text in zip(en_texts, zh_texts):
                self.assertTrue(en_text and en_text.strip(), name)
                self.assertTrue(zh_text and zh_text.strip(), name)
                self.assertEqual(FORMAT.findall(en_text), FORMAT.findall(zh_text), name)

    def test_android_language_settings_advertise_both_supported_locales(self):
        namespace = "{http://schemas.android.com/apk/res/android}"
        locales = ET.parse(RES / "xml" / "locales_config.xml").getroot()
        self.assertEqual(["en", "zh-Hans"], [node.attrib[namespace + "name"] for node in locales])
