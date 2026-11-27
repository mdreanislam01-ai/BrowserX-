package com.browserx.app

/**
 * Minimal DNS / UDP / IPv4 helpers used by [ShieldVpnService].
 *
 * Kept free of Android APIs so the packet handling can be unit tested on the JVM.
 */
internal object ShieldDns {

    data class Question(val host: String, val endOffset: Int, val type: Int)

    /** Parses the (single) question of a DNS query. */
    fun parseQuestion(dns: ByteArray): Question? {
        if (dns.size < 16) return null
        if (readU16(dns, 4) < 1) return null // QDCOUNT
        var offset = 12
        val name = StringBuilder()
        while (offset < dns.size) {
            val labelLength = dns[offset].toInt() and 0xFF
            offset++
            if (labelLength == 0) break
            if (labelLength > 63 || offset + labelLength > dns.size) return null
            if (name.isNotEmpty()) name.append('.')
            name.append(String(dns, offset, labelLength, Charsets.US_ASCII))
            offset += labelLength
        }
        if (offset + 4 > dns.size) return null
        val type = readU16(dns, offset)
        return Question(name.toString().lowercase(), offset + 4, type)
    }

    /**
     * Answers a blocked host: 0.0.0.0 for A queries, an empty NOERROR answer for anything else
     * (AAAA and friends), which is what DNS-based blockers return.
     */
    fun blockedResponse(query: ByteArray, question: Question): ByteArray {
        val questionLength = question.endOffset - 12
        val withAnswer = question.type == 1 // A
        val out = ByteArray(12 + questionLength + if (withAnswer) 16 else 0)
        out[0] = query[0]
        out[1] = query[1]
        out[2] = 0x81.toByte() // QR + RD
        out[3] = 0x80.toByte() // RA, rcode 0 (NOERROR)
        writeU16(out, 4, 1)                        // QDCOUNT
        writeU16(out, 6, if (withAnswer) 1 else 0) // ANCOUNT
        System.arraycopy(query, 12, out, 12, questionLength)
        if (withAnswer) {
            val p = 12 + questionLength
            out[p] = 0xC0.toByte() // pointer to the question name
            out[p + 1] = 0x0C
            writeU16(out, p + 2, 1)   // TYPE A
            writeU16(out, p + 4, 1)   // CLASS IN
            out[p + 9] = 60           // TTL 60s
            writeU16(out, p + 10, 4)  // RDLENGTH
            // RDATA stays 0.0.0.0
        }
        return out
    }

    /** SERVFAIL, used when no upstream resolver answered. */
    fun errorResponse(query: ByteArray, question: Question): ByteArray {
        val questionLength = question.endOffset - 12
        val out = ByteArray(12 + questionLength)
        out[0] = query[0]
        out[1] = query[1]
        out[2] = 0x81.toByte()
        out[3] = 0x82.toByte() // rcode 2 = SERVFAIL
        writeU16(out, 4, 1)
        System.arraycopy(query, 12, out, 12, questionLength)
        return out
    }

    /** Wraps a DNS payload in an IPv4 + UDP packet addressed back to the app. */
    fun buildUdpPacket(
        sourceIp: ByteArray,
        destinationIp: ByteArray,
        destinationPort: Int,
        payload: ByteArray
    ): ByteArray {
        require(sourceIp.size == 4) { "source IP must be IPv4" }
        require(destinationIp.size == 4) { "destination IP must be IPv4" }
        val totalLength = 20 + 8 + payload.size
        val out = ByteArray(totalLength)
        out[0] = 0x45
        writeU16(out, 2, totalLength)
        out[6] = 0x40 // don't fragment
        out[8] = 64   // TTL
        out[9] = 17   // UDP
        System.arraycopy(sourceIp, 0, out, 12, 4)
        System.arraycopy(destinationIp, 0, out, 16, 4)
        writeU16(out, 20, 53)                      // source port = DNS
        writeU16(out, 22, destinationPort)
        writeU16(out, 24, 8 + payload.size)
        writeU16(out, 26, 0)                       // UDP checksum optional for IPv4
        System.arraycopy(payload, 0, out, 28, payload.size)
        writeU16(out, 10, checksum(out, 20))
        return out
    }

    /** RFC 1071 one's complement checksum over [length] bytes; the field must be zeroed. */
    fun checksum(data: ByteArray, length: Int): Int {
        var sum = 0
        var i = 0
        while (i < length) {
            sum += readU16(data, i)
            i += 2
        }
        while (sum shr 16 != 0) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }
        return sum.inv() and 0xFFFF
    }

    fun readU16(data: ByteArray, offset: Int): Int =
        ((data[offset].toInt() and 0xFF) shl 8) or (data[offset + 1].toInt() and 0xFF)

    fun writeU16(data: ByteArray, offset: Int, value: Int) {
        data[offset] = ((value shr 8) and 0xFF).toByte()
        data[offset + 1] = (value and 0xFF).toByte()
    }
}
