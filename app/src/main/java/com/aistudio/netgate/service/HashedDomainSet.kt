package com.aistudio.netgate.service

/**
 * Membership-only set of normalized blocked domains.
 *
 * "Maximum protection" can hold ~450k domains. As a `HashSet<String>` that costs tens of megabytes
 * (the char arrays, the String headers and one hash-table node per entry), which is exactly what
 * forced the matcher to drop enabled lists on mid-range phones. This set stores each domain as one
 * packed `Long` instead - a 55-bit FNV-1a hash in the high bits plus the owning list index in the
 * low byte - so a full ruleset fits in a few megabytes.
 *
 * Domain strings are deliberately not retained, so [iterator] is unsupported: callers only need
 * [contains], [size] and [HashedDomainSet.ownerOf].
 */
class HashedDomainSet private constructor(
    private val packed: LongArray
) : Set<String> {

    override val size: Int get() = packed.size

    override fun isEmpty(): Boolean = packed.isEmpty()

    override fun contains(element: String): Boolean = indexOf(hashOf(element)) >= 0

    override fun containsAll(elements: Collection<String>): Boolean = elements.all { contains(it) }

    /**
     * Owner list index recorded for [domain], or [NO_OWNER] when the domain is absent or was added
     * without an owner.
     */
    fun ownerOf(domain: String): Int {
        val index = indexOf(hashOf(domain))
        if (index < 0) return NO_OWNER
        return (packed[index] and OWNER_MASK).toInt()
    }

    override fun iterator(): Iterator<String> = throw UnsupportedOperationException(
        "HashedDomainSet keeps hashes only; domain strings are not retained"
    )

    override fun toString(): String = "HashedDomainSet(size=$size)"

    private fun indexOf(hash: Long): Int {
        var low = 0
        var high = packed.size - 1
        while (low <= high) {
            val mid = (low + high) ushr 1
            val probe = packed[mid] ushr OWNER_BITS
            when {
                probe < hash -> low = mid + 1
                probe > hash -> high = mid - 1
                else -> return mid
            }
        }
        return -1
    }

    /** Accumulates domains with an exact distinct count, then hands over a sorted immutable set. */
    class Builder(initialCapacity: Int = 1024) {
        private var values = LongArray(initialCapacity.coerceAtLeast(16))
        private var count = 0

        /**
         * Open-addressed "already seen" table of `hash + 1` values (0 means empty). It makes [add]
         * O(1) and keeps [uniqueSize] exact without sorting inside the build loop; the builder is
         * discarded once [build] returns.
         */
        private var seen = LongArray(64)
        private var seenMask = seen.size - 1
        private var seenCount = 0

        /** Distinct domains added so far. */
        val uniqueSize: Int get() = count

        /** Returns true when the domain was not already present. */
        fun add(domain: String, owner: Int = NO_OWNER): Boolean {
            // Keep the table at most half full so linear probing stays short.
            if ((seenCount + 1) * 2 > seen.size) growTable()
            val hash = hashOf(domain)
            val key = hash + 1L
            var slot = (hash xor (hash ushr 32)).toInt() and seenMask
            while (true) {
                val existing = seen[slot]
                if (existing == 0L) break
                if (existing == key) return false
                slot = (slot + 1) and seenMask
            }
            seen[slot] = key
            seenCount++
            if (count == values.size) {
                values = values.copyOf(values.size * 2)
            }
            values[count++] = (hash shl OWNER_BITS) or (owner.toLong() and OWNER_MASK)
            return true
        }

        private fun growTable() {
            val old = seen
            seen = LongArray(old.size shl 1)
            seenMask = seen.size - 1
            seenCount = 0
            for (value in old) {
                if (value == 0L) continue
                val hash = value - 1L
                var slot = (hash xor (hash ushr 32)).toInt() and seenMask
                while (seen[slot] != 0L) slot = (slot + 1) and seenMask
                seen[slot] = value
                seenCount++
            }
        }

        fun build(): HashedDomainSet {
            // One sort at the end: hashes are already distinct, so binary search only needs order.
            java.util.Arrays.sort(values, 0, count)
            return HashedDomainSet(values.copyOf(count))
        }

    }

    companion object {
        /** Owner byte meaning "no filter list recorded"; list indexes only occupy 0..119. */
        const val NO_OWNER = 255

        private const val OWNER_BITS = 8
        private const val OWNER_MASK = 0xFFL
        // 55 bits (not 56) so `hash shl 8` stays a positive Long and signed ordering matches hash
        // ordering. For 450k domains the collision chance is ~1 in 1.4 million.
        private const val HASH_BITS = 55
        private const val HASH_MASK = (1L shl HASH_BITS) - 1L
        private const val FNV_OFFSET_BASIS = -0x340d631b7bdddcdbL
        private const val FNV_PRIME = 0x100000001b3L

        val EMPTY = HashedDomainSet(LongArray(0))

        internal fun hashOf(domain: String): Long {
            var hash = FNV_OFFSET_BASIS
            for (index in domain.indices) {
                hash = hash xor domain[index].code.toLong()
                hash *= FNV_PRIME
            }
            return hash and HASH_MASK
        }
    }
}
