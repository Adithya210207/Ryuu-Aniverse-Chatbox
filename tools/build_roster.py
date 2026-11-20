"""Builds knowledge/roster.json from the roster table in plan.md section 11.
The plan table stays the human source; re-run this after changing it.
Run from the project folder:  py tools/build_roster.py"""
import json, re, pathlib

ROOT = pathlib.Path(__file__).resolve().parent.parent

SERIES = {
    "Code Geass": "code_geass", "Death Note": "death_note", "Classroom of the Elite": "classroom_of_the_elite",
    "Bleach": "bleach", "Chainsaw Man": "chainsaw_man", "Monster": "monster", "Bungo Stray Dogs": "bungo_stray_dogs",
    "Tomodachi Game": "tomodachi_game", "Attack on Titan": "attack_on_titan", "Naruto": "naruto",
    "Jujutsu Kaisen": "jujutsu_kaisen", "Solo Leveling": "solo_leveling", "One Punch Man": "one_punch_man",
    "JoJo's Bizarre Adventure": "jojo", "Hunter × Hunter": "hunter_x_hunter", "Sword Art Online": "sword_art_online",
    "One Piece": "one_piece", "Demon Slayer": "demon_slayer", "Dragon Ball": "dragon_ball", "Slime": "tensura",
    "Mushoku Tensei": "mushoku_tensei", "Re:Zero": "re_zero", "Spy × Family": "spy_x_family",
    "My Dress-Up Darling": "my_dress_up_darling", "Kakegurui": "kakegurui", "High School DxD": "high_school_dxd",
    "The Quintessential Quintuplets": "quintessential_quintuplets", "Darling in the FranXX": "darling_in_the_franxx",
    "Frieren": "frieren", "My Hero Academia": "my_hero_academia", "Oshi no Ko": "oshi_no_ko",
    "Tokyo Revengers": "tokyo_revengers", "ERASED": "erased", "Assassination Classroom": "assassination_classroom",
    "Blue Exorcist": "blue_exorcist", "Oregairu": "oregairu", "Gintama": "gintama", "Steins;Gate": "steins_gate",
    "Fullmetal Alchemist": "fullmetal_alchemist", "Dr. Stone": "dr_stone", "Fate": "fate",
}

IDS = {
    1: "lelouch_lamperouge", 2: "light_yagami", 3: "kiyotaka_ayanokoji", 4: "sosuke_aizen", 5: "makima",
    6: "johan_liebert", 7: "osamu_dazai", 8: "yuuichi_katagiri", 9: "eren_yeager", 10: "itachi_uchiha",
    11: "satoru_gojo", 12: "ryomen_sukuna", 13: "madara_uchiha", 14: "obito_uchiha", 15: "sung_jinwoo",
    16: "saitama", 17: "dio_brando", 18: "meruem", 19: "kirito", 20: "naruto_uzumaki",
    21: "sasuke_uchiha", 22: "monkey_d_luffy", 23: "roronoa_zoro", 24: "kakashi_hatake", 25: "levi_ackerman",
    26: "tanjiro_kamado", 27: "killua_zoldyck", 28: "goku", 29: "vegeta", 30: "rimuru_tempest",
    31: "rudeus_greyrat", 32: "subaru_natsuki", 33: "yor_forger", 34: "marin_kitagawa", 35: "mikasa_ackerman",
    36: "nico_robin", 37: "asuna_yuuki", 38: "yumeko_jabami", 39: "rias_gremory", 40: "rem",
    41: "miku_nakano", 42: "zero_two", 43: "hinata_hyuga", 44: "ichigo_kurosaki", 45: "orihime_inoue",
    46: "minato_namikaze", 47: "pain_nagato", 48: "shikamaru_nara", 49: "kisuke_urahara", 50: "kenpachi_zaraki",
    51: "byakuya_kuchiki", 52: "shanks", 53: "trafalgar_law", 54: "sanji", 55: "portgas_d_ace",
    56: "toji_fushiguro", 57: "suguru_geto", 58: "denji", 59: "power", 60: "aki_hayakawa",
    61: "reze", 62: "anya_forger", 63: "loid_forger", 64: "damian_desmond", 65: "kyojuro_rengoku",
    66: "giyu_tomioka", 67: "shinobu_kocho", 68: "zenitsu_agatsuma", 69: "inosuke_hashibira", 70: "katsuki_bakugo",
    71: "shoto_todoroki", 72: "izuku_midoriya", 73: "all_might", 74: "tomura_shigaraki", 75: "emilia",
    76: "ram", 77: "beatrice", 78: "frieren", 79: "fern", 80: "jiraiya",
    81: "aqua_hoshino", 82: "ai_hoshino", 83: "ruby_hoshino", 84: "manjiro_sano", 85: "ken_ryuguji",
    86: "takemichi_hanagaki", 87: "kento_nanami", 88: "satoru_fujinuma", 89: "karma_akabane", 90: "rin_okumura",
    91: "edward_newgate", 92: "hachiman_hikigaya", 93: "gintoki_sakata", 94: "rintaro_okabe", 95: "edward_elric",
    96: "alphonse_elric", 97: "senku_ishigami", 98: "artoria_pendragon", 99: "gilgamesh", 100: "shota_aizawa",
}

WAVES = {
    1: [10, 13, 14, 20, 21, 24, 43, 46, 47, 48, 80],
    2: [22, 23, 36, 52, 53, 54, 55, 91, 28, 29],
    3: [4, 44, 45, 49, 50, 51, 11, 12, 56, 57, 87],
    4: [26, 65, 66, 67, 68, 69, 9, 25, 35, 41],
    5: [70, 71, 72, 73, 74, 100, 33, 62, 63, 64],
    6: [5, 58, 59, 60, 61, 32, 40, 75, 76, 77],
    7: [81, 82, 83, 84, 85, 86, 78, 79, 95, 96],
    8: [18, 27, 98, 99, 1, 2, 3, 6, 7, 8],
    9: [15, 16, 17, 19, 37, 30, 31, 34, 38, 39],
    10: [42, 88, 89, 90, 92, 93, 94, 97],
}
WAVE_OF = {n: w for w, ns in WAVES.items() for n in ns}

def main():
    text = (ROOT / "plan.md").read_text(encoding="utf-8")
    sec = text[text.index("## 11. The full character roster"):text.index("## 12.")]
    row = re.compile(r"^\| (\d+) \| ([^|]+)\| ([^|]+)\| ([^|]+)\| ([^|]+)\| `(#[0-9A-Fa-f]{6})` \| ([^|]+) \|$", re.M)
    out = []
    for m in row.finditer(sec):
        n = int(m.group(1)); band = m.group(5).strip()
        snap = m.group(4).strip()
        out.append({
            "number": n, "file": f"{n:03d}_{IDS[n]}", "id": IDS[n], "name": m.group(2).strip(),
            "series_title": m.group(3).strip(), "series": SERIES[m.group(3).strip()],
            "snapshot": snap.replace("🔄 ", ""), "ongoing": snap.startswith("🔄"),
            "age_band": band.split(" ")[0], "age_band_confirm": "agent confirms" in band,
            "accent": m.group(6), "glyph": m.group(7).strip(), "wave": WAVE_OF[n],
        })
    assert len(out) == 100, len(out)
    assert sorted(r["number"] for r in out) == list(range(1, 101))
    assert sorted(WAVE_OF) == list(range(1, 101))
    doc = {"version": 1, "notes": "Generated from plan.md section 11 by tools/build_roster.py. Edit the plan table, then re-run.",
           "characters": out}
    (ROOT / "knowledge" / "roster.json").write_text(json.dumps(doc, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    series = sorted({r["series"] for r in out})
    print(f"roster: {len(out)} characters, {len(series)} series, {sum(r['ongoing'] for r in out)} ongoing")

if __name__ == "__main__":
    main()
