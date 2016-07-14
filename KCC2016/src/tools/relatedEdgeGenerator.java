package tools;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map.Entry;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class relatedEdgeGenerator {

	public static void main(String[] args) {
		// TODO Auto-generated method stub
		String nodeDir = "C:/Users/WonKyung/git/KCC2016/DJproject/DJMap_v1.1.nod.xml";
		String edgeDir = "C:/Users/WonKyung/git/KCC2016/DJproject/DJMap_v1.1.edg.xml";
		String relatedEdgesDir = "C:/Users/WonKyung/git/KCC2016/DJproject/relatedEdges.txt";

		LinkedHashMap<String, ArrayList<String>> relEdges = new LinkedHashMap<String, ArrayList<String>>();

		try {
			DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
			DocumentBuilder db = dbf.newDocumentBuilder();
			Document doc = db.parse(new File(nodeDir));

			//일단 노드를 뽑아내서 hashmap에 넣어둠. 
			NodeList nList = doc.getElementsByTagName("node");
			for (int i=0; i<nList.getLength(); i++){
				Element n = (Element)nList.item(i);
				relEdges.put(n.getAttribute("id"), new ArrayList<String>());
			}

			//edge 파싱하여 hashmap에 알맞게 넣음.
			doc = db.parse(new File(edgeDir));
			nList = doc.getElementsByTagName("edge");
			for (int i=0; i<nList.getLength(); i++){
				Element n = (Element)nList.item(i);
				ArrayList<String> edges = relEdges.get(n.getAttribute("from"));
				if (!(edges.contains(n.getAttribute("id")))){
					edges.add(n.getAttribute("id"));
					relEdges.put(n.getAttribute("from"), edges);
				}
				
				edges = relEdges.get(n.getAttribute("to"));
				if (!(edges.contains(n.getAttribute("id")))){
					edges.add(n.getAttribute("id"));
					relEdges.put(n.getAttribute("to"), edges);
				}
			}
			
			BufferedWriter fw = new BufferedWriter(new FileWriter(relatedEdgesDir, false));
			for (Entry<String, ArrayList<String>> entry: relEdges.entrySet()){
				fw.write(entry.getKey()+"\t");
				for (String edg: entry.getValue())
					fw.write(edg+"\t");
				fw.newLine();
			}
			fw.close();
			
			System.out.println("Success.");

		} catch (SAXException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ParserConfigurationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}
