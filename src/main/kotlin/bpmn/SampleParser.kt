package bpmn

import cns.bpmn.engine.parser.BPMNParser
import java.io.File

fun main() {
    val bpmXml = File("sample.bpmn.xml").readText(Charsets.UTF_8)
    val bpmnDescriptor = BPMNParser.parseBpmn(bpmXml)

    bpmnDescriptor["Process_1"]?.let { process ->
        process.wfObjMap["Task_1"]?.let { task ->
            println("Task found: ${task.elemName}")
        }

        process.wfObjMap["Activity_0xd2qcp"]?.let { task ->
            println("Task found: ${task.elemName}")
        }
    } ?: println("Process not found")
}