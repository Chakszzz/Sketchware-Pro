package ide.sketchware.codeproject.build;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

/**
 * Merges AndroidManifest.xml declarations from resolved AARs and built-in libraries
 * into a temporary merged manifest for AAPT2 linking without mutating the project's
 * source manifest.
 */
public final class CodeProjectManifestMerger {

    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";
    private static final String TOOLS_NS = "http://schemas.android.com/tools";
    private static final String XMLNS_NS = "http://www.w3.org/2000/xmlns/";

    private static final Set<String> TOP_LEVEL_TAGS = new HashSet<>(Arrays.asList(
            "uses-permission",
            "uses-permission-sdk-23",
            "permission",
            "uses-feature",
            "queries"
    ));

    private static final Set<String> APPLICATION_COMPONENT_TAGS = new HashSet<>(Arrays.asList(
            "activity",
            "activity-alias",
            "service",
            "receiver",
            "provider",
            "meta-data",
            "uses-library"
    ));

    private CodeProjectManifestMerger() {
    }

    /**
     * Merges {@code libraryManifests} into {@code appManifest}, resolving {@code ${applicationId}}
     * placeholders and relative library component names, and writes the result to {@code outputFile}.
     */
    public static File merge(
            File appManifest,
            List<File> libraryManifests,
            String applicationId,
            File outputFile) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();

        Document mainDoc = builder.parse(appManifest);
        Element mainManifest = mainDoc.getDocumentElement();
        if (mainManifest == null) {
            throw new IllegalArgumentException("App manifest has no root element: " + appManifest);
        }

        String appPackage = mainManifest.getAttribute("package");
        String effectiveAppId = (applicationId != null && !applicationId.isEmpty())
                ? applicationId
                : appPackage;

        ensureAndroidNamespace(mainManifest);
        replacePlaceholders(mainManifest, effectiveAppId);

        Element mainApplication = findFirstChildElement(mainManifest, "application");
        if (mainApplication == null) {
            mainApplication = mainDoc.createElement("application");
            mainManifest.appendChild(mainApplication);
        }

        // Index existing declarations in the app manifest so app declarations always win
        Set<String> existingTopLevelKeys = new HashSet<>();
        collectElementKeys(mainManifest, TOP_LEVEL_TAGS, appPackage, existingTopLevelKeys);

        Set<String> existingAppKeys = new HashSet<>();
        collectElementKeys(mainApplication, APPLICATION_COMPONENT_TAGS, appPackage, existingAppKeys);

        if (libraryManifests != null) {
            for (File libFile : libraryManifests) {
                if (libFile == null || !libFile.isFile()) continue;

                Document libDoc;
                try {
                    libDoc = builder.parse(libFile);
                } catch (Exception ignored) {
                    continue;
                }

                Element libManifest = libDoc.getDocumentElement();
                if (libManifest == null) continue;
                String libPackage = libManifest.getAttribute("package");

                // Copy any extra xmlns declarations (e.g. xmlns:tools) from library manifest
                copyExtraNamespaces(libManifest, mainManifest);

                // Merge top-level permissions / features / queries
                NodeList libTopChildren = libManifest.getChildNodes();
                for (int i = 0; i < libTopChildren.getLength(); i++) {
                    Node node = libTopChildren.item(i);
                    if (node.getNodeType() != Node.ELEMENT_NODE) continue;
                    Element elem = (Element) node;
                    String tag = localName(elem);
                    if (!TOP_LEVEL_TAGS.contains(tag)) continue;
                    if (isToolsRemove(elem)) continue;

                    if ("queries".equals(tag)) {
                        Element imported = (Element) mainDoc.importNode(elem, true);
                        replacePlaceholders(imported, effectiveAppId);
                        mainManifest.insertBefore(imported, mainApplication);
                        continue;
                    }

                    String name = getAndroidName(elem);
                    String key = tag + ":" + (name != null ? name : "");
                    if (existingTopLevelKeys.add(key)) {
                        Element imported = (Element) mainDoc.importNode(elem, true);
                        replacePlaceholders(imported, effectiveAppId);
                        mainManifest.insertBefore(imported, mainApplication);
                    }
                }

                // Merge <application> components (activities, services, receivers, providers, meta-data)
                Element libApplication = findFirstChildElement(libManifest, "application");
                if (libApplication != null) {
                    NodeList libAppChildren = libApplication.getChildNodes();
                    for (int i = 0; i < libAppChildren.getLength(); i++) {
                        Node node = libAppChildren.item(i);
                        if (node.getNodeType() != Node.ELEMENT_NODE) continue;
                        Element elem = (Element) node;
                        String tag = localName(elem);
                        if (!APPLICATION_COMPONENT_TAGS.contains(tag)) continue;
                        if (isToolsRemove(elem)) continue;

                        String rawName = getAndroidName(elem);
                        String resolvedName = resolveComponentName(tag, rawName, libPackage);
                        String key = tag + ":" + (resolvedName != null ? resolvedName : "");
                        if (existingAppKeys.add(key)) {
                            Element imported = (Element) mainDoc.importNode(elem, true);
                            if (resolvedName != null && !resolvedName.equals(rawName)) {
                                setAndroidName(imported, resolvedName);
                            }
                            replacePlaceholders(imported, effectiveAppId);
                            mainApplication.appendChild(imported);
                        }
                    }
                }
            }
        }

        File parent = outputFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer transformer = tf.newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.transform(new DOMSource(mainDoc), new StreamResult(outputFile));
        return outputFile;
    }

    private static void ensureAndroidNamespace(Element root) {
        if (!root.hasAttributeNS(XMLNS_NS, "android")) {
            root.setAttributeNS(XMLNS_NS, "xmlns:android", ANDROID_NS);
        }
    }

    private static void copyExtraNamespaces(Element source, Element target) {
        NamedNodeMap attrs = source.getAttributes();
        for (int i = 0; i < attrs.getLength(); i++) {
            Attr attr = (Attr) attrs.item(i);
            String name = attr.getName();
            if (name.startsWith("xmlns:") && !target.hasAttribute(name)) {
                target.setAttributeNS(XMLNS_NS, name, attr.getValue());
            }
        }
    }

    private static void collectElementKeys(
            Element parent,
            Set<String> allowedTags,
            String pkg,
            Set<String> keys) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) continue;
            Element elem = (Element) node;
            String tag = localName(elem);
            if (!allowedTags.contains(tag)) continue;
            String name = resolveComponentName(tag, getAndroidName(elem), pkg);
            keys.add(tag + ":" + (name != null ? name : ""));
        }
    }

    private static Element findFirstChildElement(Element parent, String tagName) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE && tagName.equals(localName((Element) node))) {
                return (Element) node;
            }
        }
        return null;
    }

    private static String localName(Element elem) {
        return elem.getLocalName() != null ? elem.getLocalName() : elem.getTagName();
    }

    private static boolean isToolsRemove(Element elem) {
        String nodeAction = elem.getAttributeNS(TOOLS_NS, "node");
        return "remove".equals(nodeAction) || "removeAll".equals(nodeAction);
    }

    private static String getAndroidName(Element elem) {
        if (elem.hasAttributeNS(ANDROID_NS, "name")) {
            return elem.getAttributeNS(ANDROID_NS, "name");
        }
        if (elem.hasAttribute("android:name")) {
            return elem.getAttribute("android:name");
        }
        return null;
    }

    private static void setAndroidName(Element elem, String value) {
        if (elem.hasAttributeNS(ANDROID_NS, "name")) {
            elem.setAttributeNS(ANDROID_NS, "android:name", value);
        } else {
            elem.setAttribute("android:name", value);
        }
    }

    private static String resolveComponentName(String tag, String rawName, String pkg) {
        if (rawName == null || rawName.isEmpty() || pkg == null || pkg.isEmpty()) {
            return rawName;
        }
        if ("meta-data".equals(tag) || "uses-library".equals(tag)
                || "uses-permission".equals(tag) || "uses-permission-sdk-23".equals(tag)
                || "permission".equals(tag) || "uses-feature".equals(tag)) {
            return rawName;
        }
        if (rawName.startsWith(".")) {
            return pkg + rawName;
        }
        if (!rawName.contains(".")) {
            return pkg + "." + rawName;
        }
        return rawName;
    }

    private static void replacePlaceholders(Element element, String applicationId) {
        if (applicationId == null || applicationId.isEmpty()) return;
        NamedNodeMap attrs = element.getAttributes();
        for (int i = 0; i < attrs.getLength(); i++) {
            Attr attr = (Attr) attrs.item(i);
            String val = attr.getValue();
            if (val != null && val.contains("${applicationId}")) {
                attr.setValue(val.replace("${applicationId}", applicationId));
            }
        }
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                replacePlaceholders((Element) child, applicationId);
            }
        }
    }
}
