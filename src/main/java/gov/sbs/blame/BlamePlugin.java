package gov.sbs.blame;

/*
 * Copyright 2001-2005 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.io.FileUtils;
import org.apache.maven.doxia.sink.Sink;
import org.apache.maven.doxia.siterenderer.Renderer;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.reporting.AbstractMavenReport;
import org.apache.maven.reporting.MavenReportException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * 
 * @author matthewazukas
 *
 */
@Mojo(name = "blame",defaultPhase = LifecyclePhase.COMPILE)
public class BlamePlugin extends AbstractMavenReport {
	@Parameter(defaultValue = "${project.basedir}")
	private File baseDir;
	
	@Parameter(defaultValue = "${project}", required = true)
	private MavenProject mavenProject;

    @Component
    private Renderer siteRenderer;
    
	private static final List<String> STATIC_REPORT_FILE_NAMES = Arrays.asList(
			"cabbagepatch.gif",
			"notsofast.gif"
	);
	

	public String getDescription(Locale arg0) {
    	return "Returns who's at fault for PMD issues";
	}

	public String getName(Locale arg0) {
		return "Blame Tool";
	}

	public String getOutputName() {
		return "Blame.html";
	}

	@Override
	protected void executeReport(Locale locale) throws MavenReportException {
		
		try {
			File pmdFile = new File("target/pmd.xml");
			if (!pmdFile.exists()) {
				getLog().error("Could not find the PMD XML file located in [root]/target.  Did you run PMD first?");
				return;
			}
			
			Map<String, List<PmdViolation>> pmdViolations = getPmdViolations(pmdFile);
			
			ClassLoader classLoader = this.getClass().getClassLoader();
			for (String fileName : STATIC_REPORT_FILE_NAMES) {
				
				FileUtils.copyInputStreamToFile(
						classLoader.getResourceAsStream(fileName),
						new File(getReportDirectory() + "/site/images", fileName)
				);
				
			}
			
			List<PmdViolation> allGuiltyParties = new ArrayList<PmdViolation>();
			for (String key : pmdViolations.keySet()) {
				allGuiltyParties.addAll(pmdViolations.get(key));
			}
			
			createEmbeddedReportPage(locale, generateHtml(allGuiltyParties));
		} catch (Exception ex) {
			getLog().error(ex);
		}
	}

	@Override
	protected String getOutputDirectory() {
		return getReportDirectory().getAbsolutePath();
	}
	
    protected File getReportDirectory() {
        return new File(baseDir.getAbsolutePath(), "target");
    }

	@Override
	protected MavenProject getProject() {
		return mavenProject;
	}

	@Override
	protected Renderer getSiteRenderer() {
		return siteRenderer;
	}
	
    private void createEmbeddedReportPage(Locale locale, String source) {
        Sink sink = getSink();
        sink.head();
        sink.title();
        sink.text(getName(locale));
        sink.title_();
        sink.head_();

        sink.body();
        sink.rawText(source);
        sink.body_();

        sink.flush();
        sink.close();
    }
    
    private Map<String, List<PmdViolation>> getPmdViolations(File pmdFile) throws SAXException, IOException, ParserConfigurationException {
    	Map<String, List<PmdViolation>> vMap = new HashMap<String, List<PmdViolation>>();
    	
    	String fileRoot = new File("").getAbsolutePath();

		File fXmlFile = new File("target/pmd.xml");
		DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
		DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
		Document doc = dBuilder.parse(fXmlFile);

		// optional, but recommended
		// read this -
		// http://stackoverflow.com/questions/13786607/normalization-in-dom-parsing-with-java-how-does-it-work
		doc.getDocumentElement().normalize();

		NodeList filesList = doc.getElementsByTagName("file");

		for (int fileCounter = 0; fileCounter < filesList.getLength(); fileCounter++) {

			Element fileNode = (Element) filesList.item(fileCounter);
			String fileName = fileNode.getAttributes().getNamedItem("name").getNodeValue();

			// Modify the path to what GIT will understand
			fileName = fileName.replace(fileRoot + "/", "");
			
			Map<Integer, String> lineAndUser = getWhoDidIt("git blame " + fileName);

			if (!vMap.containsKey(fileName)) {
				vMap.put(fileName, new ArrayList<PmdViolation>());
			}

			NodeList violationsList = fileNode.getElementsByTagName("violation");

			for (int violationCounter = 0; violationCounter < violationsList.getLength(); violationCounter++) {
				Node violationNode = violationsList.item(violationCounter);

				String startLine = violationNode.getAttributes().getNamedItem("beginline").getNodeValue();
				String endLine = violationNode.getAttributes().getNamedItem("endline").getNodeValue();
				String rule = violationNode.getAttributes().getNamedItem("rule").getNodeValue();
				String msg = violationNode.getTextContent();

				PmdViolation pmdViolation = new PmdViolation(	fileName,
																new Integer(startLine), 
																new Integer(endLine), 
																rule,
																msg);
				
				pmdViolation.setViolator(lineAndUser.get(pmdViolation.getStart()));
				vMap.get(fileName).add(pmdViolation);
			}
		}
		
		return vMap;
    }
    
	private Map<Integer, String> getWhoDidIt(String command) {

		Map<Integer, String> lineAndPerson = new HashMap<Integer, String>();
		
		String nameRegex = Pattern.quote("(") + "(.*?)" + Pattern.quote(" ");
		Pattern namePattern = Pattern.compile(nameRegex);
		
		Pattern linePattern = Pattern.compile("\\s(\\w+)$");

		
		Process p;
		try {
			p = Runtime.getRuntime().exec(command);
			p.waitFor();
			BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));

			String line = "";
			while ((line = reader.readLine()) != null) {
				//Need to clean the line up first to avoid picking up things in the code
				String[] cleanLine = line.split("\\)");
				line = cleanLine[0];
				
				String name = "";
				Integer lineNumber = null;
				
				Matcher nameMatcher = namePattern.matcher(line);
				while (nameMatcher.find()) {
				  name = nameMatcher.group(1); // Since (.*?) is capturing group 1
				  
				}

				Matcher lineMatcher = linePattern.matcher(line.trim());
				while(lineMatcher.find()){
					lineNumber = new Integer(lineMatcher.group().trim());
				}
				
				lineAndPerson.put(lineNumber, name);
			}

		} catch (Exception e) {
			e.printStackTrace();
		}
		return lineAndPerson;
	}
	
	public String generateHtml(List<PmdViolation> violations) {
		
			
			Collections.sort(violations, new Comparator<PmdViolation>() {
				public int compare(PmdViolation o1, PmdViolation o2) {
					return o1.getViolator().compareTo(o2.getViolator());
				}
			});
			
			StringBuffer masterSB = new StringBuffer();
			
			    
		    int counter = 0;
		    Map<String, Integer> violationCounts = new HashMap<String, Integer>();
		    Date now = new Date();
		    masterSB.append("<h1>Total team violations as of " + now + " : [" + violations.size() + "]</h1>");
		    
		    if (violations.size() == 0) {
		    	masterSB.append("<br/><img src=\"images/cabbagepatch.gif\"");
		    } else {
		    
			    String violatorName = "";
			    masterSB.append("<table>");
			    StringBuffer sb = new StringBuffer();
				for (PmdViolation v : violations) {
					
					if (!violatorName.equals(v.getViolator())) {
						counter = 1;
						violatorName = v.getViolator();
						sb.append("<tr><td colspan=3>&nbsp;</td></tr>");
						sb.append("<tr>");
							sb.append("<td colspan=3>");
								sb.append("<b>" + v.getViolator() + " <font style=\"color: red;\">(Total Violations {"+v.getViolator()+"})</font></b>");
							sb.append("</td>");	
						sb.append("</tr>");
					}
					
					violationCounts.put("{"+v.getViolator()+"}", new Integer(counter++));
					
					sb.append("<tr>");
						sb.append("<td>");
							sb.append("<b>" + v.getFileName() + "</b>");
						sb.append("</td>");
						sb.append("<td style=\"padding-left: 10px; padding-right: 10px;\">");
							sb.append("Line " + v.getStart());
						sb.append("</td>");
						sb.append("<td>");
							sb.append(v.getRule());
						sb.append("</td>");
					sb.append("</tr>");
					sb.append("<tr>");
						sb.append("<td colspan=3 style=\"padding-left: 10px;\">");
							sb.append(v.getMsg());
						sb.append("</td>");
					sb.append("</tr>");
				}
				
				String rawOutput = sb.toString();
				String leadingName = "";
				int leadingCount = 0;
				for (String name : violationCounts.keySet()) {
					Integer violationCount = violationCounts.get(name);
					rawOutput = rawOutput.replace(name, violationCount.toString());
					
					if (leadingCount < violationCount) {
						leadingCount = violationCount;
						leadingName = name.replaceAll("\\{", "").replaceAll("\\}", "");
					}
				}
				
				masterSB.append(rawOutput);
				
				masterSB.append("</table>");
				
				masterSB.append("<br /><br />And the biggest violator is <u>" + leadingName + "</u> with " + leadingCount + " violations"); 
				
				masterSB.append("<br/><img src=\"images/notsofast.gif\"");
		    }
		    
		return masterSB.toString();
	}
}
