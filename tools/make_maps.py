"""Generates the level maps for Compile Quest (res/levels/levelN.txt).

Usage (from the project folder):  python tools/make_maps.py res/levels

WARNING: this overwrites res/levels/level0.txt ... level4.txt. If you edited a map by hand,
do not run this script afterwards. It also prints each level's comment platforms in reading
order; the "comments" array in levelN.json must list them in that same order.

Coordinates are tiles: x to the right, y down. "Floor top row" means the row of the
first solid tile the player stands on. After changing a map, run:
    java -cp CompileQuest.jar com.compilequest.Main --mapcheck
"""
import json
import os
import sys

OUT = sys.argv[1] if len(sys.argv) > 1 else "."
os.makedirs(OUT, exist_ok=True)


class Map:
    def __init__(self, w, h):
        self.w, self.h = w, h
        self.g = [[' '] * w for _ in range(h)]
        self.comments = []

    def fill(self, x0, y0, x1, y1, c='#'):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.g[y][x] = c

    def put(self, x, y, c):
        self.g[y][x] = c

    def border(self, floor_top):
        self.fill(0, 0, self.w - 1, 1)
        self.fill(0, 0, 1, self.h - 1)
        self.fill(self.w - 2, 0, self.w - 1, self.h - 1)
        self.fill(0, floor_top, self.w - 1, self.h - 1)

    def comment(self, x, y, text, width=None):
        for i in range(width or len(text)):
            self.g[y][x + i] = '-'
        self.comments.append((y, x, text))

    def save(self, name):
        with open(os.path.join(OUT, name + ".txt"), "w", newline="\n") as f:
            f.write("\n".join("".join(r) for r in self.g) + "\n")
        texts = [t for (_, _, t) in sorted(self.comments)]
        print(name, json.dumps(texts))


# ---------------------------------------------------------------- Level 0: Hello World
m = Map(96, 30)
m.border(26)
m.fill(21, 24, 33, 25)            # step, top row 24
m.fill(36, 21, 47, 25)            # ledge, top row 21
m.comment(48, 21, "// S to drop down")
m.fill(65, 2, 67, 23)             # wall hanging from the ceiling
m.fill(65, 19, 66, 20, ' ')       # star pocket
m.fill(80, 24, 82, 25)            # second step
m.comment(3, 22, "// A/D to move")
m.comment(21, 20, "// W: jump, W again: double jump", 12)
m.comment(36, 16, "// Tab: open the editor")
m.comment(70, 21, "// E at javac: run code")
m.put(4, 25, 'P')
m.put(9, 25, 'S')
m.put(41, 18, '1')
m.put(56, 24, '2')
m.put(77, 25, 'J')
m.put(90, 25, 'E')
m.put(5, 19, '*')
m.put(66, 20, '*')
m.put(89, 19, '*')
m.save("level0")

# ---------------------------------------------------------------- Level 1: Variables
m = Map(112, 30)
m.border(26)
m.fill(2, 20, 6, 20)              # top-left star ledge (6 above floor)
m.put(4, 18, '*')
m.put(5, 25, 'P')
m.put(8, 25, 'S')
m.put(11, 26, '+')                # spring set into the floor
m.comment(13, 21, "// ++ launches you up")
m.fill(38, 25, 40, 25, '^')       # null spikes
m.put(39, 22, '2')                # decoy: 3
m.comment(35, 21, "// null hurts")
m.put(47, 26, '+')
m.fill(49, 20, 55, 21)            # high platform (6 above floor)
m.put(52, 18, '1')                # correct: 5
m.put(60, 24, 'G')
m.fill(63, 23, 65, 25)            # step, 3 high
m.comment(67, 22, "// mind the gap")
m.fill(70, 25, 76, 25, '^')
m.put(79, 20, '3')                # decoy: "5"
m.put(86, 26, '+')
m.fill(83, 20, 84, 20)            # small star ledge
m.put(83, 18, '*')
m.put(90, 25, 'J')
m.fill(93, 24, 95, 25)            # step, 2 high
m.fill(96, 25, 98, 25, 'L')       # lift, 3 wide
m.fill(99, 16, 109, 25)           # plateau, top row 16 (10 above floor: out of reach without the lift)
m.put(106, 15, 'E')
m.put(103, 13, '*')
m.save("level1")

# ---------------------------------------------------------------- Level 2: if / else
m = Map(122, 30)
m.border(26)
m.put(4, 25, 'P')
m.put(7, 25, 'S')
m.comment(3, 21, "// Click or J: shoot ->")
m.put(12, 25, '#')                # bug fence
m.put(20, 25, 'b')
m.fill(31, 24, 33, 25)            # bug fence / step
m.put(24, 22, '1')                # door.open();
m.fill(34, 23, 36, 25)            # step, top row 23
m.fill(37, 22, 58, 25)            # key block, top row 22
m.fill(55, 24, 58, 25, ' ')       # hidden pocket under the block
m.put(56, 25, '*')
m.put(42, 21, 'b')
m.put(52, 21, 'b')
m.put(57, 21, 'K')
m.comment(43, 19, "// up here")
m.put(47, 17, '*')
m.put(63, 24, '2')                # alarm();
m.put(66, 25, 'S')
m.put(69, 24, 'G')
m.put(72, 25, 'J')
m.comment(70, 20, "// if (hasKey)")
m.put(79, 25, 'A')
m.put(82, 25, 'A')
m.fill(84, 2, 92, 21)             # corridor ceiling
m.fill(86, 22, 87, 25, 'D')       # the door
m.fill(97, 23, 99, 25)            # after the door
m.put(104, 25, 'b')
m.fill(108, 23, 111, 25)
m.comment(100, 20, "// almost")
m.put(104, 18, '*')
m.put(116, 25, 'E')
m.save("level2")

# ---------------------------------------------------------------- Level 3: for loop
m = Map(132, 34)
m.border(26)
m.put(4, 25, 'P')
m.put(7, 25, 'S')
m.comment(3, 20, "// break: shoot it")
m.fill(21, 2, 24, 21)             # ceiling over the break wall
m.fill(22, 22, 22, 25, 'B')       # break wall
# transient bridge over a spike pit
m.fill(30, 26, 41, 27, ' ')
m.fill(30, 27, 41, 27, '^')
m.fill(31, 23, 33, 23, 'T')
m.fill(36, 23, 38, 23, 'T')
m.put(37, 20, '1')                # <
m.comment(29, 18, "// transient")
m.put(34, 20, '*')
# exception corridor
m.fill(44, 24, 46, 25)            # step up to the comment
m.put(52, 24, '2')                # <=
m.comment(48, 22, "// catch the throw")
m.put(60, 20, '3')                # 8
m.put(66, 25, 'x')
m.fill(69, 20, 70, 20)
m.put(69, 18, '*')
m.put(71, 24, 'G')
# upper ledge with transient stairs
m.fill(73, 23, 75, 23, 'T')
m.fill(77, 20, 86, 21)            # upper ledge, top row 20
m.put(81, 18, '5')                # print
m.put(87, 24, '6')                # println
m.fill(92, 22, 96, 23)            # alcove roof
m.fill(92, 24, 92, 25, 'B')       # break door of the alcove
m.put(94, 24, '4')                # 5
m.fill(96, 24, 96, 25)
m.put(98, 25, 'S')
m.put(101, 25, 'J')
# river tunnel
m.fill(104, 2, 114, 22)
m.fill(104, 23, 114, 23, 'F')     # low final ceiling
m.fill(106, 26, 113, 26, '_')     # bridge hint
m.fill(106, 27, 113, 28, '~')     # river
m.fill(106, 24, 113, 24, 'v')     # null hanging over the river: no hopping across a short bridge
m.fill(114, 26, 114, 30, ' ')     # shaft down to the lower tunnel
m.fill(115, 2, 117, 28, 'F')      # final wall after the shaft
m.fill(114, 29, 129, 30, ' ')     # lower tunnel
m.fill(118, 2, 129, 18)
m.fill(119, 19, 129, 28, ' ')     # exit room
m.fill(118, 19, 118, 28)
m.comment(121, 28, "// done")
m.put(125, 26, '*')
m.put(126, 30, 'E')
m.save("level3")

# ---------------------------------------------------------------- Level 4: arrays
m = Map(128, 40)
m.border(36)
m.put(4, 35, 'P')
m.put(7, 35, 'S')
m.comment(16, 27, "// volatile: blink")
# volatile platforms over spikes
m.fill(20, 36, 33, 37, ' ')
m.fill(20, 37, 33, 37, '^')
m.fill(22, 33, 24, 33, 'V')
m.fill(27, 33, 29, 33, 'F')      # solid rest between the blinking platforms
m.fill(31, 33, 32, 33, 'V')
m.put(28, 30, '1')                # >
m.put(23, 29, '*')
# final stairs
m.fill(38, 34, 40, 35, 'F')
m.fill(41, 32, 43, 35, 'F')
m.fill(44, 30, 46, 35, 'F')
m.put(45, 27, '2')                # <
m.fill(47, 32, 49, 35, 'F')
m.put(41, 28, '*')
# moving platform over water
m.fill(52, 36, 70, 38, '~')
m.fill(52, 33, 55, 33, 'M')
m.put(60, 29, '3')                # h.length
m.comment(48, 28, "// while(true) { ride(); }")
m.put(72, 36, '+')
m.put(72, 30, '4')                # h.length - 1
m.put(75, 35, 'S')
# pillar room
m.fill(74, 2, 125, 15)
m.comment(80, 18, "// sort the pillars")
m.put(78, 35, 'J')
m.put(82, 35, 'Q')
m.fill(94, 24, 125, 35)           # exit ledge, 12 above floor, 4 tiles after the last pillar
m.put(112, 23, 'E')
m.put(83, 24, '*')
m.save("level4")
