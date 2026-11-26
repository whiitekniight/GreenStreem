package com.example.greenstreem

/** Supply NTLM's digest directly; Android's built-in BC provider cannot load app classes. */
internal class SmbDigestProvider : java.security.Provider("GreenStreemSMB",1.0,"SMB authentication digest") {
    init {
        putService(object: Service(this,"MessageDigest","MD4",SmbMd4::class.java.name,emptyList(),emptyMap()) {
            override fun newInstance(parameter: Any?): Any = SmbMd4()
        })
    }
}
internal class SmbMd4 : java.security.MessageDigest("MD4") {
    private val digest=org.bouncycastle.crypto.digests.MD4Digest()
    override fun engineUpdate(input: Byte) { digest.update(input) }
    override fun engineUpdate(input: ByteArray, offset: Int, len: Int) { digest.update(input,offset,len) }
    override fun engineDigest(): ByteArray = ByteArray(16).also { digest.doFinal(it,0) }
    override fun engineReset() { digest.reset() }
    override fun engineGetDigestLength() = 16
}
