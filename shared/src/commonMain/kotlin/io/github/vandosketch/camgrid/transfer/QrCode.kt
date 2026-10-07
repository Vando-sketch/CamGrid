package io.github.vandosketch.camgrid.transfer

import kotlin.math.abs

/**
 * A QR code (ISO/IEC 18004) of a short text, for the transfer address on the TV screen: byte
 * mode, error correction level M, versions 1 to 6 (up to 106 bytes, plenty for
 * "http://<address>:<port>"). Small on purpose; the URL is short and this avoids a library on
 * every platform. [size] modules square; [isDark] reads one module (row [y], column [x]).
 */
class QrCode private constructor(val size: Int, private val modules: Array<BooleanArray>) {

    fun isDark(x: Int, y: Int): Boolean = modules[y][x]

    companion object {
        /** Largest text, in UTF-8 bytes, that [encode] accepts (version 6-M). */
        const val MAX_BYTES = 106

        /** Encodes [text]; throws [IllegalArgumentException] when it is longer than [MAX_BYTES]. */
        fun encode(text: String): QrCode {
            val data = text.encodeToByteArray()
            val version = (1..MAX_VERSION).firstOrNull { dataCapacity(it) >= data.size + 2 }
                ?: throw IllegalArgumentException("Text too long for a QR code")
            val codewords = addErrorCorrection(dataCodewords(data, version), version)
            return Builder(version).build(codewords)
        }

        private const val MAX_VERSION = 6

        /** Error correction codewords per block and number of blocks for level M, by version. */
        private val EC_PER_BLOCK = intArrayOf(0, 10, 16, 26, 18, 24, 16)
        private val BLOCKS = intArrayOf(0, 1, 1, 1, 2, 2, 4)

        /** The second alignment pattern coordinate (the first is always 6); none in version 1. */
        private val ALIGNMENT = intArrayOf(0, 0, 18, 22, 26, 30, 34)

        private fun totalCodewords(version: Int): Int {
            // Modules left for data after the function patterns; versions 1 to 6 have no version info.
            var modules = (16 * version + 128) * version + 64
            if (version >= 2) modules -= 25 * 2 * 2 - 10 * 2 - 55
            return modules / 8
        }

        private fun dataCapacity(version: Int) = totalCodewords(version) - EC_PER_BLOCK[version] * BLOCKS[version]

        /** Mode, length, data, terminator and padding, as codewords. */
        private fun dataCodewords(data: ByteArray, version: Int): IntArray {
            val capacity = dataCapacity(version)
            val bits = ArrayList<Boolean>(capacity * 8)
            fun put(value: Int, count: Int) = (count - 1 downTo 0).forEach { bits += (value shr it) and 1 == 1 }
            put(0b0100, 4) // byte mode
            put(data.size, 8) // 8-bit length in versions 1 to 9
            data.forEach { put(it.toInt() and 0xFF, 8) }
            repeat(minOf(4, capacity * 8 - bits.size)) { bits += false }
            while (bits.size % 8 != 0) bits += false
            val result = IntArray(capacity)
            for (i in 0 until bits.size / 8) {
                result[i] = (0 until 8).fold(0) { acc, b -> (acc shl 1) or if (bits[i * 8 + b]) 1 else 0 }
            }
            for (i in bits.size / 8 until capacity) result[i] = if ((i - bits.size / 8) % 2 == 0) 0xEC else 0x11
            return result
        }

        /** Splits [data] into blocks, adds Reed-Solomon codewords to each and interleaves them. */
        private fun addErrorCorrection(data: IntArray, version: Int): IntArray {
            val blockCount = BLOCKS[version]
            val ecLength = EC_PER_BLOCK[version]
            val shortLength = data.size / blockCount
            val longBlocks = data.size % blockCount
            val generator = generator(ecLength)
            val blocks = ArrayList<IntArray>()
            val ecBlocks = ArrayList<IntArray>()
            var offset = 0
            for (b in 0 until blockCount) {
                // The last blocks are one codeword longer when the data does not split evenly.
                val length = shortLength + if (b >= blockCount - longBlocks) 1 else 0
                val block = data.copyOfRange(offset, offset + length)
                offset += length
                blocks += block
                ecBlocks += remainder(block, generator)
            }
            val result = ArrayList<Int>()
            for (i in 0..shortLength) blocks.forEach { if (i < it.size) result += it[i] }
            for (i in 0 until ecLength) ecBlocks.forEach { result += it[i] }
            return result.toIntArray()
        }

        /** Generator polynomial (x - a^0)...(x - a^(degree-1)), highest coefficient (1) left out. */
        private fun generator(degree: Int): IntArray {
            val result = IntArray(degree).also { it[degree - 1] = 1 }
            var root = 1
            repeat(degree) {
                for (j in 0 until degree) {
                    result[j] = gfMultiply(result[j], root) xor if (j + 1 < degree) result[j + 1] else 0
                }
                root = gfMultiply(root, 0x02)
            }
            return result
        }

        private fun remainder(data: IntArray, generator: IntArray): IntArray {
            val result = IntArray(generator.size)
            for (value in data) {
                val factor = value xor result[0]
                result.copyInto(result, 0, 1)
                result[result.size - 1] = 0
                for (i in result.indices) result[i] = result[i] xor gfMultiply(generator[i], factor)
            }
            return result
        }

        /** Multiplication in GF(2^8) modulo x^8 + x^4 + x^3 + x^2 + 1. */
        private fun gfMultiply(x: Int, y: Int): Int {
            var result = 0
            for (i in 7 downTo 0) {
                result = (result shl 1) xor ((result ushr 7) * 0x11D)
                result = result xor ((y ushr i) and 1) * x
            }
            return result
        }
    }

    /** Draws the function patterns, places the codewords and picks the mask with the lowest penalty. */
    private class Builder(version: Int) {
        val size = version * 4 + 17
        val modules = Array(size) { BooleanArray(size) }
        val function = Array(size) { BooleanArray(size) }

        init {
            for (i in 0 until size) {
                set(6, i, i % 2 == 0)
                set(i, 6, i % 2 == 0)
            }
            finder(3, 3)
            finder(size - 4, 3)
            finder(3, size - 4)
            if (ALIGNMENT[version] != 0) alignment(ALIGNMENT[version], ALIGNMENT[version])
            formatBits(0) // reserves the format areas; drawn again with the chosen mask
        }

        fun build(codewords: IntArray): QrCode {
            placeData(codewords)
            val best = (0 until 8).minBy { mask ->
                applyMask(mask)
                formatBits(mask)
                penalty().also { applyMask(mask) } // XOR again undoes the mask
            }
            applyMask(best)
            formatBits(best)
            return QrCode(size, modules)
        }

        fun set(x: Int, y: Int, dark: Boolean) {
            modules[y][x] = dark
            function[y][x] = true
        }

        /** Finder pattern centred on ([cx], [cy]) with its light separator. */
        fun finder(cx: Int, cy: Int) {
            for (dy in -4..4) for (dx in -4..4) {
                val x = cx + dx
                val y = cy + dy
                if (x !in 0 until size || y !in 0 until size) continue
                val distance = maxOf(abs(dx), abs(dy))
                set(x, y, distance != 2 && distance != 4)
            }
        }

        fun alignment(cx: Int, cy: Int) {
            for (dy in -2..2) for (dx in -2..2) set(cx + dx, cy + dy, maxOf(abs(dx), abs(dy)) != 1)
        }

        /** The 15 format bits (level M, [mask]) in both copies, plus the dark module. */
        fun formatBits(mask: Int) {
            val data = (0b00 shl 3) or mask // level M is 00
            var remainder = data
            repeat(10) { remainder = (remainder shl 1) xor ((remainder ushr 9) * 0x537) }
            val bits = ((data shl 10) or remainder) xor 0x5412
            fun bit(i: Int) = (bits ushr i) and 1 == 1
            for (i in 0..5) set(8, i, bit(i))
            set(8, 7, bit(6))
            set(8, 8, bit(7))
            set(7, 8, bit(8))
            for (i in 9..14) set(14 - i, 8, bit(i))
            for (i in 0..7) set(size - 1 - i, 8, bit(i))
            for (i in 8..14) set(8, size - 15 + i, bit(i))
            set(8, size - 8, true)
        }

        /** Codeword bits in the zigzag order: two-column strips from the right, alternately up and down. */
        fun placeData(codewords: IntArray) {
            var i = 0
            var right = size - 1
            while (right >= 1) {
                if (right == 6) right = 5 // the vertical timing pattern
                for (vertical in 0 until size) {
                    for (j in 0..1) {
                        val x = right - j
                        val upward = (right + 1) and 2 == 0
                        val y = if (upward) size - 1 - vertical else vertical
                        if (!function[y][x] && i < codewords.size * 8) {
                            modules[y][x] = (codewords[i ushr 3] ushr (7 - (i and 7))) and 1 == 1
                            i++
                        }
                    }
                }
                right -= 2
            }
        }

        fun applyMask(mask: Int) {
            for (y in 0 until size) for (x in 0 until size) {
                if (function[y][x]) continue
                val invert = when (mask) {
                    0 -> (x + y) % 2 == 0
                    1 -> y % 2 == 0
                    2 -> x % 3 == 0
                    3 -> (x + y) % 3 == 0
                    4 -> (x / 3 + y / 2) % 2 == 0
                    5 -> x * y % 2 + x * y % 3 == 0
                    6 -> (x * y % 2 + x * y % 3) % 2 == 0
                    else -> ((x + y) % 2 + x * y % 3) % 2 == 0
                }
                if (invert) modules[y][x] = !modules[y][x]
            }
        }

        /** The standard's penalty score: long runs, 2x2 blocks, finder-like patterns and dark balance. */
        fun penalty(): Int {
            var score = 0
            for (horizontal in listOf(true, false)) {
                for (a in 0 until size) {
                    val line = BooleanArray(size) { b -> if (horizontal) modules[a][b] else modules[b][a] }
                    var run = 1
                    for (b in 1..size) {
                        if (b < size && line[b] == line[b - 1]) {
                            run++
                        } else {
                            if (run >= 5) score += run - 2
                            run = 1
                        }
                    }
                    for (b in 0..size - FINDER_LIKE.size) {
                        if (FINDER_LIKE.indices.all { line[b + it] == FINDER_LIKE[it] } ||
                            FINDER_LIKE.indices.all { line[b + it] == FINDER_LIKE[FINDER_LIKE.size - 1 - it] }
                        ) {
                            score += 40
                        }
                    }
                }
            }
            for (y in 0 until size - 1) for (x in 0 until size - 1) {
                val c = modules[y][x]
                if (modules[y][x + 1] == c && modules[y + 1][x] == c && modules[y + 1][x + 1] == c) score += 3
            }
            val dark = modules.sumOf { row -> row.count { it } }
            score += abs(dark * 100 / (size * size) - 50) / 5 * 10
            return score
        }

        companion object {
            /** Dark-light-dark-dark-dark-light-dark followed by four light modules. */
            val FINDER_LIKE = booleanArrayOf(true, false, true, true, true, false, true, false, false, false, false)
        }
    }
}
