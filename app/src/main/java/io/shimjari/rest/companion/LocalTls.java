package io.shimjari.rest.companion;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import java.io.*;
import java.net.InetSocketAddress;
import java.security.*;
import java.security.cert.*;
import java.math.BigInteger;
import java.util.Date;
import javax.net.ssl.*;
import javax.security.auth.x500.X500Principal;
import org.json.JSONObject;

public final class LocalTls {
    private static final String ALIAS="shimjari_vehicle_tls_v2";
    public static final int PORT=44389,MAX_FRAME=32768;
    private LocalTls(){}
    public static String hash(byte[] value)throws Exception{return hex(MessageDigest.getInstance("SHA-256").digest(value));}
    public static String hex(byte[] value){StringBuilder s=new StringBuilder();for(byte b:value)s.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return s.toString();}
    public static String display(String pin){return pin.substring(0,4).toUpperCase(java.util.Locale.ROOT)+" "+pin.substring(4,8).toUpperCase(java.util.Locale.ROOT)+" "+pin.substring(8,12).toUpperCase(java.util.Locale.ROOT);}
    public static String random(){byte[] b=new byte[32];new SecureRandom().nextBytes(b);return hex(b);}
    private static KeyStore identity()throws Exception{
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
        if(!store.containsAlias(ALIAS)){
            KeyPairGenerator g=KeyPairGenerator.getInstance("RSA","AndroidKeyStore");
            g.initialize(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_SIGN|KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(2048).setDigests(KeyProperties.DIGEST_NONE,KeyProperties.DIGEST_SHA256,KeyProperties.DIGEST_SHA384,KeyProperties.DIGEST_SHA512)
                .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1,KeyProperties.SIGNATURE_PADDING_RSA_PSS)
                // Conscrypt TLS uses a raw private RSA operation for the already encoded signature.
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE,KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1)
                .setCertificateSubject(new X500Principal("CN=Shimjari local vehicle"))
                .setCertificateSerialNumber(BigInteger.valueOf(new SecureRandom().nextInt(Integer.MAX_VALUE)+1L))
                .setCertificateNotBefore(new Date(System.currentTimeMillis()-86400000L))
                .setCertificateNotAfter(new Date(System.currentTimeMillis()+10L*365*86400000L)).build());g.generateKeyPair();
        }
        return store;
    }
    public static String serverFingerprint()throws Exception{return hash(identity().getCertificate(ALIAS).getEncoded());}
    public static SSLServerSocket server()throws Exception{
        KeyStore store=identity();
        X509KeyManager key=new X509KeyManager(){
            public String[] getClientAliases(String t,java.security.Principal[] i){return null;}
            public String chooseClientAlias(String[] t,java.security.Principal[] i,java.net.Socket s){return null;}
            public String[] getServerAliases(String t,java.security.Principal[] i){return "RSA".equals(t)?new String[]{ALIAS}:null;}
            public String chooseServerAlias(String t,java.security.Principal[] i,java.net.Socket s){return "RSA".equals(t)?ALIAS:null;}
            public X509Certificate[] getCertificateChain(String a){try{return new X509Certificate[]{(X509Certificate)store.getCertificate(ALIAS)};}catch(Exception e){return null;}}
            public PrivateKey getPrivateKey(String a){try{return (PrivateKey)store.getKey(ALIAS,null);}catch(Exception e){return null;}}
        };
        SSLContext ssl=SSLContext.getInstance("TLS");ssl.init(new KeyManager[]{key},null,new SecureRandom());
        SSLServerSocket socket=(SSLServerSocket)ssl.getServerSocketFactory().createServerSocket();
        socket.setEnabledCipherSuites(new String[]{"TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256","TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384"});
        socket.setReuseAddress(true);socket.setEnabledProtocols(new String[]{"TLSv1.2"});socket.bind(new InetSocketAddress(PORT),4);return socket;
    }
    private static SSLSocket connect(LocalEndpoint endpoint,String pinned,String[] capture)throws Exception{
        if(pinned!=null&&!pinned.matches("[a-f0-9]{64}"))throw new CertificateException("차량 인증 정보가 올바르지 않아요.");
        X509TrustManager trust=new X509TrustManager(){
            public X509Certificate[] getAcceptedIssuers(){return new X509Certificate[0];}
            public void checkClientTrusted(X509Certificate[] c,String type)throws CertificateException{throw new CertificateException("client certificate not accepted");}
            public void checkServerTrusted(X509Certificate[] chain,String type)throws CertificateException{
                try{
                    if(chain==null||chain.length==0)throw new CertificateException("차량 인증서가 없어요.");
                    String found=hash(chain[0].getEncoded());
                    if(pinned!=null&&!MessageDigest.isEqual(found.getBytes(java.nio.charset.StandardCharsets.US_ASCII),pinned.getBytes(java.nio.charset.StandardCharsets.US_ASCII)))throw new CertificateException("차량 인증서가 달라졌어요. 다시 페어링해 주세요.");
                    if(capture!=null)capture[0]=found;
                }catch(CertificateException e){throw e;}catch(Exception e){throw new CertificateException(e);}
            }
        };
        SSLContext ssl=SSLContext.getInstance("TLS");ssl.init(null,new TrustManager[]{trust},new SecureRandom());
        SSLSocket socket=(SSLSocket)ssl.getSocketFactory().createSocket();
        try{socket.connect(new InetSocketAddress(endpoint.host,endpoint.port),3000);socket.setSoTimeout(5000);socket.setEnabledProtocols(new String[]{"TLSv1.2"});socket.startHandshake();return socket;}
        catch(Exception e){socket.close();throw e;}
    }
    /** Probe exchanges no application data or credentials. A person must compare the displayed fingerprint. */
    public static String probe(LocalEndpoint endpoint)throws Exception{
        String[] fingerprint=new String[1];try(SSLSocket ignored=connect(endpoint,null,fingerprint)){return fingerprint[0];}
    }
    public static JSONObject request(LocalEndpoint endpoint,String pinned,JSONObject request)throws Exception{
        if(pinned==null)throw new CertificateException("차량 인증을 먼저 확인해 주세요.");
        try(SSLSocket socket=connect(endpoint,pinned,null)){write(socket.getOutputStream(),request);return read(socket.getInputStream());}
    }
    public static JSONObject read(InputStream stream)throws Exception{
        DataInputStream in=new DataInputStream(stream);int length=in.readInt();
        if(length<2||length>MAX_FRAME)throw new IOException("연결 메시지의 크기를 확인해 주세요.");byte[] data=new byte[length];in.readFully(data);return new JSONObject(new String(data,java.nio.charset.StandardCharsets.UTF_8));
    }
    public static void write(OutputStream stream,JSONObject object)throws Exception{
        byte[] data=object.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);if(data.length>MAX_FRAME)throw new IOException("연결 메시지가 너무 커요.");
        DataOutputStream out=new DataOutputStream(stream);out.writeInt(data.length);out.write(data);out.flush();
    }
}
