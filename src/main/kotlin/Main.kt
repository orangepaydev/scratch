package org.example

import org.slf4j.LoggerFactory
import java.io.InputStream
import java.security.SecureRandom
import java.util.Hashtable
import javax.naming.Context
import javax.naming.directory.InitialDirContext

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
fun main() {
    val env = Hashtable<String, String>()
    env[Context.INITIAL_CONTEXT_FACTORY] = "com.sun.jndi.ldap.LdapCtxFactory"
    env[Context.PROVIDER_URL] = "ldap://localhost:389/o=JNDITutorial"
    // Register userPKCS12 as a binary attribute so JNDI retrieves it as byte[]
    // env["java.naming.ldap.attributes.binary"] = certAttrName;

    val incomingApiKey = "correct"
    val expectedApiKey = "1correct".drop(1)

    if (incomingApiKey != expectedApiKey) {
        println("do not match")
    } else {
        println("match")
    }
    val logger = LoggerFactory.getLogger(InputStream::class.java)

    val value = SecureRandom().nextInt(Integer.MAX_VALUE)
    println(value)

    try {
        println("hello")
    }catch (e : Exception) {
        logger.error("LDAP certificate lookup failed:", e)
        logger.error("LDAP certificate lookup failed: {}", "a place holder", e)
    }


//    val ctx = InitialDirContext(env)
}