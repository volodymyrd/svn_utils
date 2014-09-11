package com.ukrsibbank.svn;

import java.io.File;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Node;

/**
 * 
 */
public class App {
	public static void main(String[] args) {
		try {
			Utils svnUtils = new Utils("", "");

			File wcDir = new File("pm");

			if (!wcDir.exists()) {
				wcDir.mkdirs();

				svnUtils.checkout(
						"https://ievv00600002.bnppua.net.intra:7443/svn/tests/pm/trunk",
						wcDir);
			} else {
				svnUtils.update(wcDir);

				File pom = new File("pm/pom.xml");

				if (!pom.exists()) {
					System.out.println("pom not found");

					return;
				}

				DocumentBuilderFactory dbf = DocumentBuilderFactory
						.newInstance();
				Document document = dbf.newDocumentBuilder().parse(pom);

				XPathFactory xpf = XPathFactory.newInstance();
				XPath xpath = xpf.newXPath();
				XPathExpression expression = xpath.compile("/project/version");

				Node version = (Node) expression.evaluate(document,
						XPathConstants.NODE);
				// b13Node.getParentNode().removeChild(b13Node);
				String v = version.getTextContent();
				v = v.substring(0, v.lastIndexOf(".")) + ".17";
				version.setTextContent(v);

				TransformerFactory tf = TransformerFactory.newInstance();
				Transformer t = tf.newTransformer();
				t.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");

				t.transform(new DOMSource(document), new StreamResult(pom));

				svnUtils.commit(pom, "Update to version: " + v);

				String targetUrl = "https://ievv00600002.bnppua.net.intra:7443/svn/tests/pm/tags/v_"
						+ v;
				svnUtils.makeWorkingDir(targetUrl, "pm_copy", v,
						"Update to version: " + v);

				svnUtils.merge("https://ievv00600002.bnppua.net.intra:7443/svn/tests/pm/trunk", "pm_copy", v);
			}

		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}