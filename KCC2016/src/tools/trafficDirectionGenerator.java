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

public class trafficDirectionGenerator {

	public static void main(String[] args) {
		// TODO Auto-generated method stub
		String nodeDir = "C:/Users/WonKyung/git/KCC2016/DJproject/DJMap_v1.1.nod.xml";
		String edgeDir = "C:/Users/WonKyung/git/KCC2016/DJproject/DJMap_v1.1.edg.xml";
		String connDir = "C:/Users/WonKyung/git/KCC2016/DJproject/DJMap_v1.1.con.xml";
		String trfDirectionDir = "C:/Users/WonKyung/git/KCC2016/DJproject/trafficDirection.txt";

		LinkedHashMap<String, ArrayList<String>> trfDirection = new LinkedHashMap<String, ArrayList<String>>();
		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		DocumentBuilder db;

		try {
			db = dbf.newDocumentBuilder();
			Document doc = db.parse(new File(nodeDir));
			//일단 노드를 뽑아내서 hashmap에 넣어둠. 
			NodeList cList = doc.getElementsByTagName("node");
			//traffic light인것만 nodesList에 추가
			for (int i=0; i<cList.getLength(); i++){
				Element n = (Element)cList.item(i);
				if(n.getAttribute("type").compareTo("traffic_light")==0)
					trfDirection.put(n.getAttribute("id"), new ArrayList<String>());
			}

			doc = db.parse(new File(connDir));
			//			Document edoc = db.parse(new File(edgeDir));
			cList = doc.getElementsByTagName("connection");
			doc = db.parse(new File(edgeDir));
			NodeList eList = doc.getElementsByTagName("edge");

			for (int i=0; i<cList.getLength(); i++){
				Element cel = (Element)cList.item(i);
				String cfrom = cel.getAttribute("from");
				String cto = cel.getAttribute("to");
				String fromNode="";
				String toNode="";
				for (int j=0; j<eList.getLength(); j++){
					Element eel = (Element)eList.item(j);
					if (eel.getAttribute("id").compareTo(cfrom)==0){
						fromNode=eel.getAttribute("to");
						break;
					}
				}
				for (int j=0; j<eList.getLength(); j++){
					Element eel = (Element)eList.item(j);
					if (eel.getAttribute("id").compareTo(cto)==0){
						toNode = eel.getAttribute("from");
						break;
					}
				}
				if (fromNode.compareTo(toNode)==0){
					if (!(getPassingNodes(nodeDir).contains(fromNode))){
						ArrayList<String> tmp = trfDirection.get(fromNode);
						String con = cfrom+"@"+cto;
//						if (!(tmp.contains(con))){
							tmp.add(con);
							trfDirection.put(fromNode,tmp);
//						}
					}
				}
			}

			BufferedWriter fw = new BufferedWriter(new FileWriter(trfDirectionDir, false));
			for (Entry<String, ArrayList<String>> entry: trfDirection.entrySet()){
				fw.write(entry.getKey()+"\t");
				for (String edg: entry.getValue())
					fw.write(edg+"\t");
				fw.newLine();
			}
			fw.close();

			System.out.println("Success.");

		} catch (ParserConfigurationException | SAXException | IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}



	private static ArrayList<String> getPassingNodes(String nodeDir){
		ArrayList<String> nodes = new ArrayList<String>();

		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		DocumentBuilder db;
		try {
			db = dbf.newDocumentBuilder();
			Document doc = db.parse(new File(nodeDir));
			//일단 노드를 뽑아내서 hashmap에 넣어둠. 
			NodeList nList = doc.getElementsByTagName("node");

			for (int i=0; i<nList.getLength(); i++){
				Element n = (Element)nList.item(i);
				if(n.getAttribute("type").compareTo("traffic_light")!=0)
					nodes.add(n.getAttribute("id"));
			}

		} catch (ParserConfigurationException | SAXException | IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return nodes;
	}
}
