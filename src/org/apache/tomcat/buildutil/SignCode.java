package org.apache.tomcat.buildutil;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import javax.xml.soap.MessageFactory;
import javax.xml.soap.SOAPBody;
import javax.xml.soap.SOAPConnection;
import javax.xml.soap.SOAPConnectionFactory;
import javax.xml.soap.SOAPConstants;
import javax.xml.soap.SOAPElement;
import javax.xml.soap.SOAPEnvelope;
import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPMessage;
import javax.xml.soap.SOAPPart;

import org.apache.tomcat.util.buf.StringUtils;
import org.apache.tomcat.util.codec.binary.Base64;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.DirectoryScanner;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.types.FileSet;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * 将文件提交给Symantec代码签名服务的Ant任务.
 */
public class SignCode extends Task {

    private static final URL SIGNING_SERVICE_URL;

    private static final String NS = "cod";

    private static final MessageFactory SOAP_MSG_FACTORY;

    static {
        try {
            SIGNING_SERVICE_URL = new URL(
                    "https://api-appsec-cws.ws.symantec.com/webtrust/SigningService");
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException(e);
        }
        try {
            SOAP_MSG_FACTORY = MessageFactory.newInstance(SOAPConstants.SOAP_1_1_PROTOCOL);
        } catch (SOAPException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private final List<FileSet> filesets = new ArrayList<>();
    private String userName;
    private String password;
    private String partnerCode;
    private String keyStore;
    private String keyStorePassword;
    private String applicationName;
    private String applicationVersion;
    private String signingService;
    private boolean debug;

    public void addFileset(FileSet fileset) {
        filesets.add(fileset);
    }


    public void setUserName(String userName) {
        this.userName = userName;
    }


    public void setPassword(String password) {
        this.password = password;
    }


    public void setPartnerCode(String partnerCode) {
        this.partnerCode = partnerCode;
    }


    public void setKeyStore(String keyStore) {
        this.keyStore = keyStore;
    }


    public void setKeyStorePassword(String keyStorePassword) {
        this.keyStorePassword = keyStorePassword;
    }


    public void setApplicationName(String applicationName) {
        this.applicationName = applicationName;
    }


    public void setApplicationVersion(String applicationVersion) {
        this.applicationVersion = applicationVersion;
    }


    public void setSigningService(String signingService) {
        this.signingService = signingService;
    }


    public void setDebug(String debug) {
        this.debug = Boolean.parseBoolean(debug);
    }


    @Override
    public void execute() throws BuildException {

        List<File> filesToSign = new ArrayList<>();

        // 处理文件集并填充需要签名的文件列表.
        for (FileSet fileset : filesets) {
            DirectoryScanner ds = fileset.getDirectoryScanner(getProject());
            File basedir = ds.getBasedir();
            String[] files = ds.getIncludedFiles();
            if (files.length > 0) {
                for (int i = 0; i < files.length; i++) {
                    File file = new File(basedir, files[i]);
                    filesToSign.add(file);
                }
            }
        }

        // 设置 TLS 客户端
        System.setProperty("javax.net.ssl.keyStore", keyStore);
        System.setProperty("javax.net.ssl.keyStorePassword", keyStorePassword);

        try {
            String signingSetID = makeSigningRequest(filesToSign);
            downloadSignedFiles(filesToSign, signingSetID);
        } catch (SOAPException | IOException e) {
            throw new BuildException(e);
        }
    }


    private String makeSigningRequest(List<File> filesToSign) throws SOAPException, IOException {
        log("Constructing the code signing request");

        SOAPMessage message = SOAP_MSG_FACTORY.createMessage();
        SOAPBody body = populateEnvelope(message, NS);

        SOAPElement requestSigning = body.addChildElement("requestSigning", NS);
        SOAPElement requestSigningRequest =
                requestSigning.addChildElement("requestSigningRequest", NS);

        addCredentials(requestSigningRequest, this.userName, this.password, this.partnerCode);

        SOAPElement applicationName =
                requestSigningRequest.addChildElement("applicationName", NS);
        applicationName.addTextNode(this.applicationName);

        SOAPElement applicationVersion =
                requestSigningRequest.addChildElement("applicationVersion", NS);
        applicationVersion.addTextNode(this.applicationVersion);

        SOAPElement signingServiceName =
                requestSigningRequest.addChildElement("signingServiceName", NS);
        signingServiceName.addTextNode(this.signingService);

        List<String> fileNames = getFileNames(filesToSign);

        SOAPElement commaDelimitedFileNames =
                requestSigningRequest.addChildElement("commaDelimitedFileNames", NS);
        commaDelimitedFileNames.addTextNode(StringUtils.join(fileNames));

        SOAPElement application =
                requestSigningRequest.addChildElement("application", NS);
        application.addTextNode(getApplicationString(fileNames, filesToSign));

        // Send the message
        SOAPConnectionFactory soapConnectionFactory = SOAPConnectionFactory.newInstance();
        SOAPConnection connection = soapConnectionFactory.createConnection();

        log("Sending singing request to server and waiting for response");
        SOAPMessage response = connection.call(message, SIGNING_SERVICE_URL);

        if (debug) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream(2 * 1024);
            response.writeTo(baos);
            log(baos.toString("UTF-8"));
        }

        log("Processing response");
        SOAPElement responseBody = response.getSOAPBody();

        // Should come back signed
        NodeList bodyNodes = responseBody.getChildNodes();
        NodeList requestSigningResponseNodes = bodyNodes.item(0).getChildNodes();
        NodeList returnNodes = requestSigningResponseNodes.item(0).getChildNodes();

        String signingSetID = null;
        String signingSetStatus = null;

        for (int i = 0; i < returnNodes.getLength(); i++) {
            Node returnNode = returnNodes.item(i);
            if (returnNode.getLocalName().equals("signingSetID")) {
                signingSetID = returnNode.getTextContent();
            } else if (returnNode.getLocalName().equals("signingSetStatus")) {
                signingSetStatus = returnNode.getTextContent();
            }
        }

        if (!signingService.contains("TEST") && !"SIGNED".equals(signingSetStatus) ||
                signingService.contains("TEST") && !"INITIALIZED".equals(signingSetStatus) ) {
            throw new BuildException("Signing failed. Status was: " + signingSetStatus);
        }

        return signingSetID;
    }


    private void downloadSignedFiles(List<File> filesToSign, String id)
            throws SOAPException, IOException {

        log("Downloading signed files. The signing set ID is: " + id);

        SOAPMessage message = SOAP_MSG_FACTORY.createMessage();
        SOAPBody body = populateEnvelope(message, NS);

        SOAPElement getSigningSetDetails = body.addChildElement("getSigningSetDetails", NS);
        SOAPElement getSigningSetDetailsRequest =
                getSigningSetDetails.addChildElement("getSigningSetDetailsRequest", NS);

        addCredentials(getSigningSetDetailsRequest, this.userName, this.password, this.partnerCode);

        SOAPElement signingSetID =
                getSigningSetDetailsRequest.addChildElement("signingSetID", NS);
        signingSetID.addTextNode(id);

        SOAPElement returnApplication =
                getSigningSetDetailsRequest.addChildElement("returnApplication", NS);
        returnApplication.addTextNode("true");

        // Send the message
        SOAPConnectionFactory soapConnectionFactory = SOAPConnectionFactory.newInstance();
        SOAPConnection connection = soapConnectionFactory.createConnection();

        log("Requesting signed files from server and waiting for response");
        SOAPMessage response = connection.call(message, SIGNING_SERVICE_URL);

        log("Processing response");
        SOAPElement responseBody = response.getSOAPBody();

        // Check for success

        // 从ZIP中提取已签名的文件
        NodeList bodyNodes = responseBody.getChildNodes();
        NodeList getSigningSetDetailsResponseNodes = bodyNodes.item(0).getChildNodes();
        NodeList returnNodes = getSigningSetDetailsResponseNodes.item(0).getChildNodes();

        String result = null;
        String data = null;

        for (int i = 0; i < returnNodes.getLength(); i++) {
            Node returnNode = returnNodes.item(i);
            if (returnNode.getLocalName().equals("result")) {
                result = returnNode.getChildNodes().item(0).getTextContent();
            } else if (returnNode.getLocalName().equals("signingSet")) {
                data = returnNode.getChildNodes().item(1).getTextContent();
            }
        }

        if (!"0".equals(result)) {
            throw new BuildException("Download failed. Result code was: " + result);
        }

        extractFilesFromApplicationString(data, filesToSign);
    }


    private static SOAPBody populateEnvelope(SOAPMessage message, String namespace)
            throws SOAPException {
        SOAPPart soapPart = message.getSOAPPart();
        SOAPEnvelope envelope = soapPart.getEnvelope();
        envelope.addNamespaceDeclaration(
                "soapenv","http://schemas.xmlsoap.org/soap/envelope/");
        envelope.addNamespaceDeclaration(
                namespace,"http://api.ws.symantec.com/webtrust/codesigningservice");
        return envelope.getBody();
    }


    private static void addCredentials(SOAPElement requestSigningRequest,
            String user, String pwd, String code) throws SOAPException {
        SOAPElement authToken = requestSigningRequest.addChildElement("authToken", NS);
        SOAPElement userName = authToken.addChildElement("userName", NS);
        userName.addTextNode(user);
        SOAPElement password = authToken.addChildElement("password", NS);
        password.addTextNode(pwd);
        SOAPElement partnerCode = authToken.addChildElement("partnerCode", NS);
        partnerCode.addTextNode(code);
    }


    /**
     * 签名服务需要唯一的文件名. 由于文件将按顺序返回, 使用已知是唯一的但保留文件扩展名的虚拟名称, 因为签名服务似乎使用它来确定要签名的内容以及如何签名.
     */
    private static List<String> getFileNames(List<File> filesToSign) {
        List<String> result = new ArrayList<>(filesToSign.size());

        for (int i = 0; i < filesToSign.size(); i++) {
            File f = filesToSign.get(i);
            String fileName = f.getName();
            int extIndex = fileName.lastIndexOf('.');
            String newName;
            if (extIndex < 0) {
                newName = Integer.toString(i);
            } else {
                newName = Integer.toString(i) + fileName.substring(extIndex);
            }
            result.add(newName);
        }
        return result;
    }


    /**
     * 压缩文件, base 64对生成的zip进行编码, 然后返回字符串.
     * 将此流直接传输到签名服务器会更有效，但需要签名的文件相对较小，这样写起来更简单.
     *
     * @param fileNames 修改过的文件名
     * @param files     要签名的文件
     */
    private static String getApplicationString(List<String> fileNames, List<File> files)
            throws IOException {
        // 16 MB 对Tomcat来说足够了
        // TODO: 重构整个类, 以便它使用流而不是在内存中缓冲整个文件集, 将使其更广泛有用.
        ByteArrayOutputStream baos = new ByteArrayOutputStream(16 * 1024 * 1024);
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            byte[] buf = new byte[32 * 1024];
            for (int i = 0; i < files.size(); i++) {
                try (FileInputStream fis = new FileInputStream(files.get(i))) {
                    ZipEntry zipEntry = new ZipEntry(fileNames.get(i));
                    zos.putNextEntry(zipEntry);
                    int numRead;
                    while ( (numRead = fis.read(buf)) >= 0) {
                        zos.write(buf, 0, numRead);
                    }
                }
            }
        }

        return Base64.encodeBase64String(baos.toByteArray());
    }


    /**
     * 删除 base64 编码, 解压缩文件并将新文件写在旧文件的顶部.
     */
    private static void extractFilesFromApplicationString(String data, List<File> files)
            throws IOException {
        ByteArrayInputStream bais = new ByteArrayInputStream(Base64.decodeBase64(data));
        try (ZipInputStream zis = new ZipInputStream(bais)) {
            byte[] buf = new byte[32 * 1024];
            for (int i = 0; i < files.size(); i ++) {
                try (FileOutputStream fos = new FileOutputStream(files.get(i))) {
                    zis.getNextEntry();
                    int numRead;
                    while ( (numRead = zis.read(buf)) >= 0) {
                        fos.write(buf, 0 , numRead);
                    }
                }
            }
        }
    }
}
