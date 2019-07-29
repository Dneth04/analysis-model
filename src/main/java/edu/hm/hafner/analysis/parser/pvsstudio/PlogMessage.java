package edu.hm.hafner.analysis.parser.pvsstudio;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import edu.hm.hafner.analysis.ReaderFactory;
import edu.hm.hafner.util.IntegerParser;

/**
 * A parser for PVS-Studio Plog/XML files.
 *
 * @author PVS-Studio Team
 */
class PlogMessage {
    private String file = "";
    private int lineNumber = 0;
    private String errorCode = "";
    private String message = "";
    private String level = "";

    public String getHash() {
        return errorCode + message + file + lineNumber;
    }

    @Override
    public String toString() {
        return message;
    }

    public String getFilePath() {
        return file;
    }

    public int getLine() {
        return lineNumber;
    }

    public String getType() {
        return errorCode;
    }

    public String getLevel() {
        return level;
    }

    /**
     * Getting list messages from report.
     * @param readerFactory - factory containing report file reader
     * @return list plog messages
     */
    public static List<PlogMessage> getMessagesFromReport(ReaderFactory readerFactory) {
        List<PlogMessage> plogMessages = new ArrayList<>();

        long failWarningsCount = 0;
        long falseAlarmCount = 0;

        Document plogDoc = readerFactory.readDocument();

        plogDoc.getDocumentElement().normalize();

        NodeList nList = plogDoc.getElementsByTagName("PVS-Studio_Analysis_Log");

        for (int nodeCount = 0; nodeCount < nList.getLength(); nodeCount++) {
            Node nNode = nList.item(nodeCount);

            if (nNode.getNodeType() == Node.ELEMENT_NODE) {
                Element eElement = (Element) nNode;

                PlogMessage msg = new PlogMessage();

                NodeList nodeFalseAlarm = eElement.getElementsByTagName("FalseAlarm");
                if (nodeFalseAlarm != null && nodeFalseAlarm.item(0) != null && nodeFalseAlarm.item(0).getTextContent().equalsIgnoreCase("true")) {
                    ++falseAlarmCount;
                    continue;
                }

                NodeList nodeFile = eElement.getElementsByTagName("File");

                if (nodeFile != null && nodeFile.item(0) != null && nodeFile.item(0).getTextContent() != null) {
                    msg.file = nodeFile.item(0).getTextContent().trim();
                }

                if (msg.file.isEmpty()) {
                    ++failWarningsCount;
                    continue;
                }

                NodeList nodeErrorCode = eElement.getElementsByTagName("ErrorCode");

                if (nodeErrorCode != null && nodeErrorCode.item(0) != null && nodeErrorCode.item(0).getTextContent() != null) {
                    msg.errorCode = nodeErrorCode.item(0).getTextContent().trim();
                }

                if (msg.errorCode.isEmpty() || msg.errorCode.charAt(0) != 'V') {
                    ++failWarningsCount;
                    continue;
                }

                msg.message = "<a target=\"_blank\" href=\"https://www.viva64.com/en/w/" + msg.errorCode.toLowerCase(Locale.ENGLISH) + "/\">"
                        + msg.errorCode + "</a> " + eElement.getElementsByTagName("Message").item(0).getTextContent();

                msg.level = eElement.getElementsByTagName("Level").item(0).getTextContent();

                msg.lineNumber = IntegerParser.parseInt(eElement.getElementsByTagName("Line").item(0).getTextContent());
                if (msg.lineNumber <= 0) {

                    ++failWarningsCount;
                    continue;
                }

                plogMessages.add(msg);
            }
        }

        if ((plogMessages.size() + falseAlarmCount) == 0 && failWarningsCount > 0) {
            Logger.getLogger(PVSStudioParser.class.getName()).log(Level.SEVERE, "No messages were parsed!");
        }

        return plogMessages;
    }
}
