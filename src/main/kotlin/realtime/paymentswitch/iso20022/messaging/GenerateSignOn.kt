package realtime.paymentswitch.iso20022.messaging

import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

class GenerateSignOn {
    companion object {
        fun generateSignOnRequest(signOnBic: String, currentTime: LocalDateTime): String {
            val millis = currentTime.atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()

            val isoTime = currentTime.atOffset(ZoneOffset.UTC).toString();

            return """
<?xml version="1.0" encoding="UTF-8"?>
<Message xmlns="urn:tc:rtp:xsd:rtp.message.01"
         xmlns:ah="urn:iso:std:iso:20022:tech:xsd:head.001.001.04"
         xmlns:admn="urn:iso:std:iso:20022:tech:xsd:admn.001.001.01">

    <!-- Business Application Header (AppHdr) -->
    <ah:AppHdr>
        <ah:Fr>
            <ah:FIId>
                <ah:FinInstnId>
                    <ah:BICFI>${signOnBic}</ah:BICFI>
                    <ah:ClrSysMmbId>
                        <ah:ClrSysId>
                            <ah:Cd>USABA</ah:Cd>
                        </ah:ClrSysId>
                        <ah:MmbId>${signOnBic}</ah:MmbId>
                    </ah:ClrSysMmbId>
                    <ah:Nm>Sending Bank NA</ah:Nm>
                </ah:FinInstnId>
            </ah:FIId>
        </ah:Fr>
        <ah:To>
            <ah:FIId>
                <ah:FinInstnId>
                    <ah:BICFI>BCTLTLDIXXX</ah:BICFI>
                    <ah:ClrSysMmbId>
                        <ah:ClrSysId>
                            <ah:Cd>USABA</ah:Cd>
                        </ah:ClrSysId>
                        <ah:MmbId>999999999</ah:MmbId>
                    </ah:ClrSysMmbId>
                    <ah:Nm>RTP Clearing House</ah:Nm>
                </ah:FinInstnId>
            </ah:FIId>
        </ah:To>
        <ah:BizMsgIdr>MSGID-SONREQ-${millis}</ah:BizMsgIdr>
        <ah:MsgDefIdr>admn.001.001.01</ah:MsgDefIdr>
        <ah:BizSvc>swift.cbprplus.01</ah:BizSvc>
        <ah:CreDt>${isoTime}</ah:CreDt>
    </ah:AppHdr>

    <!-- SignOnRequest payload -->
    <admn:SignOnRequest>
        <admn:AdmnSignOnReq>
            <admn:GrpHdr>
                <admn:MsgId>SONREQ-20260323-${millis}</admn:MsgId>
                <admn:CreDtTm>${isoTime}</admn:CreDtTm>
            </admn:GrpHdr>
            <admn:SignOnReq>
                <admn:InstrId>INSTR-20260323-0001</admn:InstrId>
                <admn:InstgAgt>
                    <admn:FinInstnId>
                        <admn:ClrSysMmbId>
                            <admn:ClrSysId>
                                <admn:Cd>USABA</admn:Cd>
                            </admn:ClrSysId>
                            <admn:MmbId>${signOnBic}</admn:MmbId>
                        </admn:ClrSysMmbId>
                    </admn:FinInstnId>
                </admn:InstgAgt>
                <admn:InstdAgt>
                    <admn:FinInstnId>
                        <admn:ClrSysMmbId>
                            <admn:ClrSysId>
                                <admn:Cd>USABA</admn:Cd>
                            </admn:ClrSysId>
                            <admn:MmbId>BCTLTLDIXXX</admn:MmbId>
                        </admn:ClrSysMmbId>
                    </admn:FinInstnId>
                </admn:InstdAgt>
            </admn:SignOnReq>
        </admn:AdmnSignOnReq>
    </admn:SignOnRequest>
</Message>
        """.trimIndent()
        }
    }
}

fun main() {
    GenerateSignOn.generateSignOnRequest("BNCTTLDDXXX", LocalDateTime.now()).also {
        println(it)
    }
}
