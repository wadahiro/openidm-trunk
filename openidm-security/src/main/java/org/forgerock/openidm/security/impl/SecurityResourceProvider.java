/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2013-2014 ForgeRock AS. All Rights Reserved
 *
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the License). You may not use this file except in
 * compliance with the License.
 *
 * You can obtain a copy of the License at
 * http://forgerock.org/license/CDDLv1.0.html
 * See the License for the specific language governing
 * permission and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL
 * Header Notice in each file and include the License file
 * at http://forgerock.org/license/CDDLv1.0.html
 * If applicable, add the following below the CDDL Header,
 * with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 */

package org.forgerock.openidm.security.impl;

import java.io.StringReader;
import java.io.StringWriter;
import java.math.BigInteger;
import java.security.Key;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.crypto.SecretKey;

import org.apache.commons.lang3.tuple.Pair;
import org.bouncycastle.asn1.x500.X500NameBuilder;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.PKCS10CertificationRequest;
import org.bouncycastle.jce.X509Principal;
import org.bouncycastle.openssl.PEMReader;
import org.bouncycastle.openssl.PEMWriter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.forgerock.json.fluent.JsonValue;
import org.forgerock.json.resource.ConnectionFactory;
import org.forgerock.json.resource.InternalServerErrorException;
import org.forgerock.json.resource.NotFoundException;
import org.forgerock.json.resource.Requests;
import org.forgerock.json.resource.Resource;
import org.forgerock.json.resource.ResourceException;
import org.forgerock.json.resource.ServerContext;
import org.forgerock.json.resource.UpdateRequest;
import org.forgerock.openidm.security.KeyStoreHandler;
import org.forgerock.openidm.security.KeyStoreManager;
import org.forgerock.openidm.util.DateUtil;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A class containing common members and methods of a Security ResourceProvider implementation.
 * 
 * @author ckienle
 */
public class SecurityResourceProvider {
    
    private final static Logger logger = LoggerFactory.getLogger(SecurityResourceProvider.class);

    public static final String BC = org.bouncycastle.jce.provider.BouncyCastleProvider.PROVIDER_NAME;
    
    public static final String ACTION_GENERATE_CERT = "generateCert";
    public static final String ACTION_GENERATE_CSR = "generateCSR";

    public static final String DEFAULT_SIGNATURE_ALGORITHM = "SHA512WithRSAEncryption";
    public static final String DEFAULT_ALGORITHM = "RSA";
    public static final String DEFAULT_CERTIFICATE_TYPE = "X509";
    public static final int DEFAULT_KEY_SIZE = 2048;

    /**
     * The Keystore handler which handles access to actual Keystore instance
     */
    protected KeyStoreHandler store = null;
    
    /**
     * The KeyStoreManager used for reloading the stores. 
     */
    protected KeyStoreManager manager = null;    
    
    /**
     * The Repository Service Accessor
     */
    protected ServerContext accessor = null;

    /**
     * The Connection Factory
     */
    protected ConnectionFactory connectionFactory;

    /**
     * The resource name, "truststore" or "keystore".
     */
    protected String resourceName = null;

    public SecurityResourceProvider(String resourceName, KeyStoreHandler store, KeyStoreManager manager, ServerContext accessor, ConnectionFactory connectionFactory) {
        this.store = store;
        this.resourceName = resourceName;
        this.manager = manager;
        this.accessor = accessor;
        this.connectionFactory = connectionFactory;
    }
    /**
     * Returns a PEM String representation of a object.
     * 
     * @param object the object
     * @return the PEM String representation
     * @throws Exception
     */
    protected String toPem(Object object) throws Exception {
        StringWriter sw = new StringWriter(); 
        PEMWriter pw = new PEMWriter(sw); 
        pw.writeObject(object); 
        pw.flush(); 
        return sw.toString();
    }
    
    /**
     * Returns an object from a PEM String representation
     * 
     * @param pem the PEM String representation
     * @return the object
     * @throws Exception
     */
    protected <T> T fromPem(String pem) throws Exception {
        StringReader sr = new StringReader(pem);
        PEMReader pw = new PEMReader(sr);
        Object object = pw.readObject();
        return (T)object;
    }
    
    /**
     * Reads a certificate from a supplied string representation, and a supplied type.
     * 
     * @param certString A String representation of a certificate
     * @param type The type of certificate ("X509").
     * @return The certificate
     * @throws Exception
     */
    protected Certificate readCertificate(String certString, String type) throws Exception {
        StringReader sr = new StringReader(certString);
        PEMReader pw = new PEMReader(sr);
        Object object = pw.readObject();
        if (object instanceof X509Certificate) {
            return (X509Certificate)object;
        } else {
            throw ResourceException.getException(ResourceException.BAD_REQUEST, "Unsupported certificate format");
        }
    }
    
    /**
     * Reads a certificate chain from a supplied string array representation, and a supplied type.
     * 
     * @param certStringChain an array of strings representing a certificate chain
     * @param type the type of certificates ("X509")
     * @return the certificate chain
     * @throws Exception
     */
    protected Certificate[] readCertificateChain(List<String> certStringChain, String type) throws Exception {
        Certificate [] certChain = new Certificate[certStringChain.size()];
        for (int i=0; i<certChain.length; i++) {
            certChain[i] = readCertificate(certStringChain.get(i), type);
        }
        return certChain;
    }

    /**
     * Returns a JsonValue map representing a certificate
     * 
     * @param alias  the certificate alias
     * @param cert  The certificate
     * @return a JsonValue map representing the certificate
     * @throws Exception
     */
    protected JsonValue returnCertificate(String alias, Certificate cert) throws Exception {
        JsonValue content = new JsonValue(new LinkedHashMap<String, Object>());
        content.put(Resource.FIELD_CONTENT_ID, alias);
        content.put("type", cert.getType());
        content.put("cert", getCertString(cert));
        content.put("publicKey", getKeyMap(cert.getPublicKey()));
        return content;
    }

    /**
     * Returns a JsonValue map representing a CSR
     * 
     * @param alias  the certificate alias
     * @param csr  The CSR
     * @return a JsonValue map representing the CSR
     * @throws Exception
     */
    protected JsonValue returnCertificateRequest(String alias, PKCS10CertificationRequest csr) throws Exception {
        JsonValue content = new JsonValue(new LinkedHashMap<String, Object>());
        content.put(Resource.FIELD_CONTENT_ID, alias);
        content.put("csr", getCertString(csr));
        content.put("publicKey", getKeyMap(csr.getPublicKey()));
        return content;
    }

    /**
     * Returns a JsonValue map representing a CSR
     * 
     * @param alias  the certificate alias
     * @param csr  The CSR
     * @return a JsonValue map representing the CSR
     * @throws Exception
     */
    protected JsonValue returnKey(String alias, Key key) throws Exception {
        JsonValue content = new JsonValue(new LinkedHashMap<String, Object>());
        content.put(Resource.FIELD_CONTENT_ID, alias);
        if (key instanceof PrivateKey) {
            content.put("privateKey", getKeyMap(key));
        } else if (key instanceof SecretKey) {
            content.put("secret", getKeyMap(key));
        }
        return content;
    }

    /**
     * Returns a JsonValue map representing key
     * 
     * @param key  The key
     * @return a JsonValue map representing the key
     * @throws Exception
     */
    protected Map<String, Object> getKeyMap(Key key) throws Exception {
        Map<String, Object> keyMap = new HashMap<String, Object>();
        keyMap.put("algorithm", key.getAlgorithm());
        keyMap.put("format", key.getFormat());
        keyMap.put("encoded", toPem(key));
        return keyMap;
    }

    /**
     * Returns a PEM formatted string representation of an object
     * 
     * @param object the object to write
     * @return a PEM formatted string representation of the object
     * @throws Exception
     */
    protected String getCertString(Object object) throws Exception {
        PEMWriter pemWriter = null;
        StringWriter sw = null;
        try {
            sw = new StringWriter();
            pemWriter = new PEMWriter(sw);
            pemWriter.writeObject(object);
            pemWriter.flush();
        } finally {
            pemWriter.close();
        }
        return sw.getBuffer().toString();
    }

    /**
     * Generates a self signed certificate using the given properties.
     *
     * @param commonName the common name to use for the new certificate
     * @param algorithm the algorithm to use
     * @param keySize the keysize to use
     * @param signatureAlgorithm the signature algorithm to use
     * @param validFrom when the certificate is valid from
     * @param validTo when the certificate is valid until
     * @return The generated certificate
     * @throws Exception
     */
    protected Pair<X509Certificate, PrivateKey> generateCertificate(String commonName, 
            String algorithm, int keySize, String signatureAlgorithm, String validFrom,
            String validTo) throws Exception {
        return generateCertificate(commonName, "None", "None", "None", "None", "None",
                algorithm, keySize, signatureAlgorithm, validFrom, validTo);
    }

        
    /**
     * Generates a self signed certificate using the given properties.
     *
     * @param commonName the subject's common name
     * @param organization the subject's organization name
     * @param organizationUnit the subject's organization unit name
     * @param stateOrProvince the subject's state or province
     * @param country the subject's country code
     * @param locatity the subject's locality
     * @param algorithm the algorithm to use
     * @param keySize the keysize to use
     * @param signatureAlgorithm the signature algorithm to use
     * @param validFrom when the certificate is valid from
     * @param validTo when the certificate is valid until
     * @return The generated certificate
     * @throws Exception
     */
    protected Pair<X509Certificate, PrivateKey> generateCertificate(String commonName, 
            String organization, String organizationUnit, String stateOrProvince, 
            String country, String locatity, String algorithm, int keySize, 
            String signatureAlgorithm, String validFrom, String validTo) throws Exception {
        
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(algorithm); // "RSA","BC"
        keyPairGenerator.initialize(keySize);
        KeyPair keyPair = keyPairGenerator.generateKeyPair();

        // Generate self-signed certificate
        X500NameBuilder builder = new X500NameBuilder(BCStyle.INSTANCE);
        builder.addRDN(BCStyle.C, country);
        builder.addRDN(BCStyle.ST, stateOrProvince);
        builder.addRDN(BCStyle.L, locatity);
        builder.addRDN(BCStyle.OU, organizationUnit);
        builder.addRDN(BCStyle.O, organization);
        builder.addRDN(BCStyle.CN, commonName);

        Date notBefore = null;
        Date notAfter = null;
        if (validFrom == null) {
            notBefore = new Date(System.currentTimeMillis() - 1000L * 60 * 60 * 24 * 30);
        } else {
            DateTime notBeforeDateTime = DateUtil.getDateUtil().parseIfDate(validFrom);
            if (notBeforeDateTime == null) {
                throw new InternalServerErrorException("Invalid date format for 'validFrom' property");
            } else {
                notBefore = notBeforeDateTime.toDate();
            }
        }
        if (validTo == null) {
            Calendar date = Calendar.getInstance();
            date.setTime(new Date());
            date.add(Calendar.YEAR, 10);
            notAfter = date.getTime();
        } else {
            DateTime notAfterDateTime = DateUtil.getDateUtil().parseIfDate(validTo);
            if (notAfterDateTime == null) {
                throw new InternalServerErrorException("Invalid date format for 'validTo' property");
            } else {
                notAfter = notAfterDateTime.toDate();
            }
        }

        BigInteger serial = BigInteger.valueOf(System.currentTimeMillis());

        X509v3CertificateBuilder v3CertGen = new JcaX509v3CertificateBuilder(builder.build(), serial, 
                notBefore, notAfter, builder.build(), keyPair.getPublic());

        ContentSigner sigGen = new JcaContentSignerBuilder(signatureAlgorithm).setProvider(BC).build(keyPair.getPrivate());

        X509Certificate cert = new JcaX509CertificateConverter().setProvider(BC).getCertificate(v3CertGen.build(sigGen));
        cert.checkValidity(new Date());
        cert.verify(cert.getPublicKey());

        return Pair.of(cert, keyPair.getPrivate());
    }
    
    /**
     * Generates a CSR request.
     * 
     * @param alias
     * @param algorithm
     * @param signatureAlgorithm
     * @param keySize
     * @param params
     * @return
     * @throws Exception
     */
    protected Pair<PKCS10CertificationRequest, PrivateKey> generateCSR(String alias, String algorithm, String signatureAlgorithm, int keySize, 
            JsonValue params) throws Exception {

        // Construct the distinguished name
        StringBuilder sb = new StringBuilder(); 
        sb.append("CN=").append(params.get("CN").required().asString().replaceAll(",", "\\\\,"));
        sb.append(", OU=").append(params.get("OU").defaultTo("None").asString().replaceAll(",", "\\\\,"));
        sb.append(", O=").append(params.get("O").defaultTo("None").asString().replaceAll(",", "\\\\,"));
        sb.append(", L=").append(params.get("L").defaultTo("None").asString().replaceAll(",", "\\\\,"));
        sb.append(", ST=").append(params.get("ST").defaultTo("None").asString().replaceAll(",", "\\\\,"));
        sb.append(", C=").append(params.get("C").defaultTo("None").asString().replaceAll(",", "\\\\,"));

        // Create the principle subject name
        X509Principal subjectName = new X509Principal(sb.toString());
        
        //store.getStore().
        
        // Generate the key pair
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(algorithm);  
        keyPairGenerator.initialize(keySize); 
        KeyPair keyPair = keyPairGenerator.generateKeyPair();
        PublicKey publicKey = keyPair.getPublic();
        PrivateKey privateKey = keyPair.getPrivate();
        
        // Generate the certificate request
        PKCS10CertificationRequest cr = new PKCS10CertificationRequest(signatureAlgorithm, subjectName, publicKey, null, privateKey);
        
        // Store the private key to use when the signed cert is return and updated
        logger.debug("Storing private key with alias {}", alias);
        storeKeyPair(alias, keyPair);
        
        return Pair.of(cr, privateKey);
    }   

    /**
     * Stores a KeyPair (associated with a CSR request on the specified alias) in the repository.
     * 
     * @param alias the alias from the CSR
     * @param keyPair the KeyPair object
     * @throws JsonResourceException
     */
    protected void storeKeyPair(String alias, KeyPair keyPair) throws ResourceException {
        if (accessor == null) {
            throw ResourceException.getException(ResourceException.INTERNAL_ERROR, "Repo router is null");
        }
        try {
            String container = "/repo/security/keys";
            JsonValue keyMap = new JsonValue(new HashMap<String, Object>());
            storeInRepo(container, alias, keyMap);
        } catch (Exception e) {
            throw ResourceException.getException(ResourceException.INTERNAL_ERROR, e.getMessage(), e);
        }
        
    }
    
    /**
     * Reads an object from the repository
     * @param id the object's id
     * @return the object
     * @throws JsonResourceException
     */
    protected JsonValue readFromRepo(String id) throws ResourceException {
        if (accessor == null) {
            throw ResourceException.getException(ResourceException.INTERNAL_ERROR, "Repo router is null");
        }
        JsonValue keyMap = new JsonValue(connectionFactory.getConnection().read(accessor, Requests.newReadRequest(id)).getContent());
        return keyMap;
    }
    
    /**
     * Stores an object in the repository
     * @param id the object's id
     * @param value the value of the object to store
     * @throws JsonResourceException
     */
    protected void storeInRepo(String container, String id, JsonValue value) throws ResourceException {
        if (accessor == null) {
            throw ResourceException.getException(ResourceException.INTERNAL_ERROR, "Repo router is null");
        }
        Resource oldResource;
        try {
            oldResource = connectionFactory.getConnection().read(accessor, Requests.newReadRequest(id));
        } catch (NotFoundException e) {
            logger.debug("creating object " + id);
            connectionFactory.getConnection().create(accessor, Requests.newCreateRequest(container, id, value));
            return;
        }
        UpdateRequest updateRequest = Requests.newUpdateRequest(container, id, value);
        updateRequest.setRevision(oldResource.getRevision());
        connectionFactory.getConnection().update(accessor, updateRequest);
    }
    
    /**
     * Returns a stored KeyPair (associated with a CSR request on the specified alias) from the repository.
     * 
     * @param alias the alias from the CSR
     * @return the KeyPair
     * @throws JsonResourceException
     */
    protected KeyPair getKeyPair(String alias) throws ResourceException {
        if (accessor == null) {
            throw ResourceException.getException(ResourceException.INTERNAL_ERROR, "Repo router is null");
        }
        String container = "/repo/security/keys";
        String id = container + "/" + alias;
        Resource keyResource = connectionFactory.getConnection().read(accessor, Requests.newReadRequest(id));
        if (keyResource.getContent().isNull()) {
            throw ResourceException.getException(ResourceException.NOT_FOUND, 
                    "Cannot find stored key for alias " + alias);
        }
        try {
            JsonValue key = keyResource.getContent().get("encoded");
            String pemString = key.asString();
            return fromPem(pemString);
        } catch (Exception e) {
            throw ResourceException.getException(ResourceException.INTERNAL_ERROR, e.getMessage(), e);
        }
    }
    
    /**
     * Verifies that the supplied private key and signed certificate match by signing/verifying some test data.
     * 
     * @param privateKey A private key
     * @param publicKey A public key
     * @throws JsonResourceException if the verification fails, or an error is encountered.
     */
    protected void verify(PrivateKey privateKey, Certificate cert) throws ResourceException {
        PublicKey publicKey = cert.getPublicKey();
        byte[] data = { 65, 66, 67, 68, 69, 70, 71, 72, 73, 74 };
        boolean verified;
        try {
            Signature signer = Signature.getInstance(privateKey.getAlgorithm());
            signer.initSign(privateKey);
            signer.update(data);
            byte[] signed = signer.sign();
            Signature verifier = Signature.getInstance(publicKey.getAlgorithm());
            verifier.initVerify(publicKey);
            verifier.update(data);
            verified = verifier.verify(signed);
        } catch (Exception e) {
            throw ResourceException.getException(ResourceException.INTERNAL_ERROR, 
                    "Error verifying private key and signed certificate", e);
        }
        if (!verified) {
            throw ResourceException.getException(ResourceException.BAD_REQUEST, 
                    "Private key does not match signed certificate");
        }
    }
}
