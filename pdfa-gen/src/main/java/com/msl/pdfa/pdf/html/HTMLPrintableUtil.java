package com.msl.pdfa.pdf.html;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.List;

import org.apache.http.HttpEntity;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.msl.pdfa.pdf.exception.UtilException;
import com.msl.pdfa.pdf.io.IOUtils;

import net.htmlparser.jericho.Attribute;
import net.htmlparser.jericho.Attributes;
import net.htmlparser.jericho.Element;
import net.htmlparser.jericho.HTMLElementName;
import net.htmlparser.jericho.OutputDocument;
import net.htmlparser.jericho.Source;
import net.htmlparser.jericho.StartTag;
import net.htmlparser.jericho.Util;

public class HTMLPrintableUtil {

	protected static final Logger logger = LoggerFactory.getLogger(HTMLPrintableUtil.class);

	public static String addMandatoryHtml(InputStream html) throws UtilException {
		try {
			Source source = new Source(html);
			return addMandatoryHtml(source);
		} catch (IOException e) {
			logger.error("Error reading HTML to parse", e);
			throw new UtilException("Error reading HTML to parse", e);
		}
	}

	public static String addMandatoryHtml(String html) throws UtilException {
		Source source = new Source(html);
		return addMandatoryHtml(source);
	}
	
	public static String replaceNbsp(String html) throws UtilException {
		return html.replace("&nbsp;", " ");
	}

	public static String getMainContent(String html, String mainContentAttribute) throws UtilException {
		Source source = new Source(html);
		return getMainContent(source, mainContentAttribute);
	}

	private static String getMainContent(Source source, String mainContentAttribute) throws UtilException {
		try {
			Element htmlElement = source.getFirstElement(HTMLElementName.HTML);
			Element bodyElement = source.getFirstElement(HTMLElementName.BODY);
			Element headElement = source.getFirstElement(HTMLElementName.HEAD);
			Element divMainContentElement = source.getElementById(mainContentAttribute);
			OutputDocument outputDocument = new OutputDocument(source);
			StringBuilder sb = new StringBuilder();
			if (htmlElement != null) {
				logger.info("html element not null, parsing full HTML");
				sb.append(htmlElement.getStartTag().toString());
				sb.append(headElement.getStartTag().toString() + headElement.getEndTag().toString());
				sb.append(bodyElement.getStartTag().toString());
				sb.append(divMainContentElement.toString());
				sb.append(bodyElement.getEndTag().toString() + htmlElement.getEndTag().toString());
			} else {
				logger.info("main-content div received, parsing HTML fragment");
				sb.append("<html>");
				sb.append("<head></head><body>");
				sb.append(divMainContentElement.toString());
				sb.append("</body></html>");
			}
			outputDocument.replace(outputDocument.getSegment(), sb.toString());
			return outputDocument.toString();
		} catch (Exception e) {
			logger.error("Error generating HTML printable", e);
			throw new UtilException("Error generating HTML printable", e);
		}
	}

	public static String moveStyleToHead(String inputHTML) throws UtilException {
		try {
			Source source = new Source(inputHTML);
			Element bodyElement = source.getFirstElement(HTMLElementName.BODY);
			Element headElement = source.getFirstElement(HTMLElementName.HEAD);
			if (bodyElement != null) {
				List<Element> styleElements = bodyElement.getAllElements("style");
				if (styleElements != null && styleElements.size() > 0) {
					OutputDocument outputDocument = new OutputDocument(source);
					for (Element styleElement : styleElements) {
						if (styleElement != null) {
							outputDocument.remove(styleElement.getStartTag());
							outputDocument.remove(styleElement.getContent());
							if (!styleElement.getStartTag().isSyntacticalEmptyElementTag()) {
								outputDocument.remove(styleElement.getEndTag());
							}
							String styleString = "<![CDATA[\n" + styleElement.getContent().toString() + "]]>";
							outputDocument.replace(styleElement.getContent(), styleString);
							outputDocument.insert(headElement.getEndTag().getBegin(), styleElement);
						}
					}
					return outputDocument.toString();
				}
			}
			return source.toString();
		} catch (Exception e) {
			logger.error("Error generating HTML printable", e);
			throw new UtilException("Error generating HTML printable", e);
		}
	}
	
	public static String addCDATAToHeadStyleTags(String inputHTML) throws UtilException {
		try {
			Source source = new Source(inputHTML);
			Element headElement = source.getFirstElement(HTMLElementName.HEAD);
			if (headElement != null) {
				List<Element> styleElements = headElement.getAllElements("style");
				if (styleElements != null && styleElements.size() > 0) {
					OutputDocument outputDocument = new OutputDocument(source);
					for (Element styleElement : styleElements) {
						if (styleElement != null) {
							if(!styleElement.getContent().toString().startsWith("<![CDATA[")){
								StringBuffer sb = new StringBuffer();
								String styleSheetContent = "<![CDATA[\n" + styleElement.getContent().toString() + "]]>";
								sb.append("<style type=\"text/css\">\n").append(styleSheetContent).append("\n</style>");
								outputDocument.replace(styleElement, sb.toString());
							}
						}
					}
					return outputDocument.toString();
				}
			}
			return source.toString();
		} catch (Exception e) {
			logger.error("Error generating HTML printable", e);
			throw new UtilException("Error generating HTML printable", e);
		}
	}

	private static String addMandatoryHtml(Source source) throws UtilException {
		try {
			Element htmlElement = source.getFirstElement(HTMLElementName.HTML);
			if (htmlElement != null) {
				logger.info("html element not null, parsing full HTML");
				return source.toString();
			} else {
				logger.info("main-content div received, parsing HTML fragment");
				OutputDocument outputDocument = new OutputDocument(source);
				StringBuilder sb = new StringBuilder();
				sb.append("<html>");
				sb.append("<head></head><body>");
				sb.append(source.toString());
				sb.append("</body></html>");
				outputDocument.replace(outputDocument.getSegment(), sb.toString());
				return outputDocument.toString();
			}
		} catch (Exception e) {
			logger.error("Error generating HTML printable", e);
			throw new UtilException("Error generating HTML printable", e);
		}
	}

	public static String addLogoFragment(String inputHTML, String attr, String logoFragmentFile) throws UtilException {
		try {
			Source source = new Source(inputHTML);
			OutputDocument outputDocument = new OutputDocument(source);
			StringBuilder sb = new StringBuilder();
			Element titleDIV = source.getFirstElement("class", attr, false);
			String logoFragment = "";
			try {
				logoFragment = Util.getString(new InputStreamReader(
						HTMLPrintableUtil.class.getClassLoader().getResourceAsStream(logoFragmentFile),
						Charset.forName("UTF-8")));
			} catch (Exception e) {
				logger.error("Error reading logo fragment html", e);
			}
			sb.setLength(0);
			sb.append("<div class=\"" + attr + "\">\n").append(logoFragment).append(titleDIV.getContent())
					.append("\n</div>");
			outputDocument.replace(titleDIV, sb.toString());
			return outputDocument.toString();
		} catch (Exception e) {
			logger.error("Error adding logo fragment", e);
			throw new UtilException("Error adding logo fragment", e);
		}
	}

	public static String removeElementByAttr(String inputHTML, String attr, String attrValue) throws UtilException {
		try {
			Source source = new Source(inputHTML);
			OutputDocument outputDocument = new OutputDocument(source);
			List<Element> elements = source.getAllElements(attr, attrValue, false);
			for (Element element : elements) {
				outputDocument.remove(element.getStartTag().getBegin(), element.getEndTag().getEnd());
			}
			return outputDocument.toString();
		} catch (Exception e) {
			logger.error("Error removing element by attr value, attr" + attr + ",attrValue:" + attrValue, e);
			throw new UtilException("Error removing element by attr value, attr" + attr + ",attrValue:" + attrValue, e);
		}
	}

	public static String addInlineStyleSheets(InputStream inputHTML, String cssFile) throws IOException {
		Source source = new Source(inputHTML);
		OutputDocument outputDocument = new OutputDocument(source);
		StringBuilder sb = new StringBuilder();
		Element headElement = source.getFirstElement(HTMLElementName.HEAD);
		String styleSheetContent;
		try {
			styleSheetContent = Util.getString(new InputStreamReader(
					HTMLPrintableUtil.class.getClassLoader().getResourceAsStream(cssFile), Charset.forName("UTF-8")));
			sb.append(headElement.getStartTag().toString());
			sb.append("<style type=\"text/css\">\n<![CDATA[\n").append(styleSheetContent).append("\n]]>\n</style>");
			sb.append(headElement.getEndTag().toString());
			outputDocument.replace(headElement, sb.append(headElement.getContent()));
		} catch (Exception e) {
			logger.error("Error reading stylesheet:" + cssFile, e);
			// don't convert if URL is invalid
		}
		return outputDocument.toString();
	}

	public static String addExternalInlineStyleSheets(URL sourceUrl, String inputHTML) throws Exception {
		Source source = new Source(inputHTML);
		OutputDocument outputDocument = new OutputDocument(source);
		StringBuilder sb = new StringBuilder();
		List<StartTag> linkStartTags = source.getAllStartTags(HTMLElementName.LINK);
		for (StartTag startTag : linkStartTags) {
			Attributes attributes = startTag.getAttributes();
			String rel = attributes.getValue("rel");
			if (!"stylesheet".equalsIgnoreCase(rel))
				continue;
			String href = attributes.getValue("href");
			if (href == null)
				continue;
			String styleSheetContent;
			try {
				styleSheetContent = Util.getString(new InputStreamReader(new URL(sourceUrl, href).openStream()));
			} catch (Exception ex) {
				logger.warn("Error adding external CSS to inline doc." + ex.getMessage());
				continue; // don't convert if URL is invalid
			}
			sb.setLength(0);
			sb.append("<style");
			Attribute typeAttribute = attributes.get("type");
			if (typeAttribute != null)
				sb.append(' ').append(typeAttribute);
			sb.append(">\n").append(styleSheetContent).append("\n</style>");
			outputDocument.replace(startTag, sb.toString());
		}
		logger.debug("Here is the document " + sourceUrl
				+ " with all external stylesheets converted to inline stylesheets:\n");
		return outputDocument.toString();
	}
	
	public static String parseImages(URL sourceUrl, String inputHTML) throws Exception {
		Source source = new Source(inputHTML);
		OutputDocument outputDocument = new OutputDocument(source);
		StringBuilder sb = new StringBuilder();
		List<StartTag> imgStartTags = source.getAllStartTags(HTMLElementName.IMG);
		for (StartTag startTag : imgStartTags) {
			String fileName = "img" + System.currentTimeMillis() + ".png";
			Attributes attributes = startTag.getAttributes();
			String src = attributes.getValue("src");
			if (src == null)
				continue;
			try {
				
				File file = new File("D:\\ECLIPSE_WORKSPACES\\PDFA\\pdf-project\\pdfa-gen\\src\\main\\resources\\pdf\\images\\" + fileName);
				URL imgUrl = new URL(sourceUrl, src);
				IOUtils.stringToFile(readUrlForPdf(imgUrl.toURI()), file);
			} catch (Exception ex) {
				logger.warn("Error adding external CSS to inline doc." + ex.getMessage());
				continue; // don't convert if URL is invalid
			}
			sb.setLength(0);
			sb.append("<img");
			for (Attribute attribute : attributes) {
				if(attribute.getName().equalsIgnoreCase("src")){
					sb.append(' ').append("src=\"pdf/images/" + fileName + "\"");
				}else{
					sb.append(' ').append(attribute);
				}
			}
			sb.append("/>");
			outputDocument.replace(startTag, sb.toString());
		}
		logger.debug("Here is the document " + sourceUrl
				+ " with all external stylesheets converted to inline stylesheets:\n");
		return outputDocument.toString();
	}
	
	public static String readUrlForPdf(URI url) throws ClientProtocolException, IOException {
		CloseableHttpClient httpclient = HttpClients.createDefault();
		HttpGet httpGet = new HttpGet(url);
		//httpGet.setHeader(HttpHeaders.CONTENT_TYPE, "application/json");
		CloseableHttpResponse response = httpclient.execute(httpGet);
		String ret = "";
		try {
			HttpEntity entity = response.getEntity();
			ret = EntityUtils.toString(entity);
		} finally {
			response.close();
		}
		return ret;
	}


}
