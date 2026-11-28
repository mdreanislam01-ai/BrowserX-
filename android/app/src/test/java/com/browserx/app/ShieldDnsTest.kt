package com.browserx.app

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The shield VPN parses DNS packets by hand, so the wire format is covered here:
 * a real query in, a valid response out.
 */
class ShieldDnsTest {

    /** Builds a normal DNS query for [name] with the given [type]. */
    private fun query(name: String, type: Int = 1, id: Int = 0x1A2B): ByteArray {
        val out = ArrayList<Byte>()
        out.add(((id shr 8) and 0xFF).toByte())
        out.add((id and 0xFF).toByte())
        out.add(0x01) // RD
        out.add(0x00)
        out.add(0x00); out.add(0x01) // QDCOUNT = 1
        out.add(0x00); out.add(0x00)
        out.add(0x00); out.add(0x00)
        out.add(0x00); out.add(0x00)
        name.split('.').forEach { label ->
            out.add(label.length.toByte())
            label.toByteArray(Charsets.US_ASCII).forEach { out.add(it) }
        }
        out.add(0x00)
        out.add(((type shr 8) and 0xFF).toByte())
        out.add((type and 0xFF).toByte())
        out.add(0x00); out.add(0x01) // CLASS IN
        return out.toByteArray()
    }

    @Test
    fun parsesTheQuestionHostAndType() {
        val question = ShieldDns.parseQuestion(query("ads.doubleclick.net"))
        assertNotNull(question)
        assertEquals("ads.doubleclick.net", question!!.host)
        assertEquals(1, question.type)
    }

    @Test
    fun parsesAAAALookupType() {
        val question = ShieldDns.parseQuestion(query("tracker.example.com", type = 28))
        assertEquals(28, question!!.type)
    }

    @Test
    fun rejectsTruncatedPackets() {
        assertNull(ShieldDns.parseQuestion(ByteArray(8)))
    }

    @Test
    fun blockedARecordPointsAtZero() {
        val request = query("ads.doubleclick.net")
        val question = ShieldDns.parseQuestion(request)!!
        val reply = ShieldDns.blockedResponse(request, question)

        assertEquals(0x1A, reply[0].toInt() and 0xFF) // transaction id preserved
        assertEquals(0x2B, reply[1].toInt() and 0xFF)
        assertEquals(0x81, reply[2].toInt() and 0xFF) // QR set
        assertEquals(0x80, reply[3].toInt() and 0xFF) // rcode 0
        assertEquals(1, ShieldDns.readU16(reply, 4))  // QDCOUNT
        assertEquals(1, ShieldDns.readU16(reply, 6))  // ANCOUNT
        val answer = 12 + (question.endOffset - 12)
        assertEquals(1, ShieldDns.readU16(reply, answer + 2))  // TYPE A
        assertEquals(4, ShieldDns.readU16(reply, answer + 10)) // RDLENGTH
        assertArrayEquals(byteArrayOf(0, 0, 0, 0), reply.copyOfRange(answer + 12, answer + 16))
    }

    @Test
    fun blockedAAAAGetsNoAnswer() {
        val request = query("ads.doubleclick.net", type = 28)
        val question = ShieldDns.parseQuestion(request)!!
        val reply = ShieldDns.blockedResponse(request, question)
        assertEquals(1, ShieldDns.readU16(reply, 4))
        assertEquals(0, ShieldDns.readU16(reply, 6))
    }

    @Test
    fun errorResponseCarriesServfail() {
        val request = query("example.com")
        val question = ShieldDns.parseQuestion(request)!!
        val reply = ShieldDns.errorResponse(request, question)
        assertEquals(0x82, reply[3].toInt() and 0xFF)
    }

    @Test
    fun udpPacketIsWellFormed() {
        val payload = ByteArray(20) { 1 }
        val packet = ShieldDns.buildUdpPacket(
            sourceIp = byteArrayOf(10, 111, 222, 2),
            destinationIp = byteArrayOf(10, 111, 222, 1),
            destinationPort = 44444,
            payload = payload
        )
        assertEquals(20 + 8 + payload.size, packet.size)
        assertEquals(20 + 8 + payload.size, ShieldDns.readU16(packet, 2)) // total length
        assertEquals(17, packet[9].toInt())                                // UDP
        assertEquals(53, ShieldDns.readU16(packet, 20))                    // source port
        assertEquals(44444, ShieldDns.readU16(packet, 22))
        assertEquals(8 + payload.size, ShieldDns.readU16(packet, 24))
        // header checksum must validate to zero when re-summed
        var sum = 0
        for (i in 0 until 20 step 2) sum += ShieldDns.readU16(packet, i)
        while (sum shr 16 != 0) sum = (sum and 0xFFFF) + (sum shr 16)
        assertEquals(0xFFFF, sum)
    }
}
