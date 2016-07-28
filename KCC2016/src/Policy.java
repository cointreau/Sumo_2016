import java.util.ArrayList;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;


public class Policy {
	String id;
	int priority;
	Factor f;
	Operation o;
	
	public Policy(Element pol) {
		// TODO Auto-generated constructor stub
		id = pol.getAttribute("id");
		priority = Integer.parseInt(pol.getAttribute("priority"));

		NodeList nl = pol.getElementsByTagName("factor");
		f = new Factor((Element)nl.item(0));
		
		nl = pol.getElementsByTagName("operation");
		o = new Operation((Element)nl.item(0));
	}

	
	class Factor {
		String location_target;
		String location_edges;		//string 형태로 쭉 들어있음
		ArrayList<String> edges = new ArrayList<String>();		//string 형태의 location edges를 파싱하여 하나씩 가지고 있음.
		String vehicle_target;			//all, ambulance
		int vehicle_number;
		String vehicle_number_sign;		//GE, G, E, LE, L 
		int time;
		
		public Factor(Element elem) {
			//location 노드 파싱
			NodeList list = elem.getElementsByTagName("location");
			String target = ((Element)list.item(0)).getAttribute("target");
			//location type 이 edges일 경우의 처리
			if (target.compareTo("edges")==0){
				location_target = target;
				NodeList enl = ((Element)list.item(0)).getElementsByTagName("edge");
				
				//토큰으로 잘라서 edges에 하나씩 저장
				for (int i=0; i<enl.getLength(); i++){
					edges.add(enl.item(i).getTextContent());
				}
			}
			
			//all일 경우의 처리
			else if (target.compareTo("all")==0){
				location_target = target;
				location_edges = "";
			}
			
			//vehicle 노드 파싱
			list = elem.getElementsByTagName("vehicle");
			vehicle_target = ((Element)list.item(0)).getAttribute("target");
			vehicle_number = Integer.parseInt(((Element)list.item(0)).getElementsByTagName("number").item(0).getTextContent());
//			vehicle_number_sign = ((Element)((NodeList)((Element)list.item(0)).getElementsByTagName("number")).item(0)).getAttribute("sign");
			vehicle_number_sign = ((Element)list.item(0)).getElementsByTagName("sign").item(0).getTextContent();
			
			list = elem.getElementsByTagName("time");
			time = Integer.parseInt(((Element)list.item(0)).getTextContent());
//			System.out.println(time);
		}
		
		public String getLocation_target(){
			return location_target;
		}
		public String getLocation_edges(){
			return location_edges;
		}
		public String getVehicle_target(){
			return vehicle_target;
		}
		public int getVehicle_number(){
			return vehicle_number;
		}
		
		public ArrayList<String> getEdges(){
			return edges;
		}
		
		public String getVehicle_number_sign(){
			return vehicle_number_sign;
		}
		public int getTime(){
			return time;
		}
	}
	
	class Operation {
		String location_target;
		String location_edges;
		ArrayList<String> edges = new ArrayList<String>();		//string 형태의 location edges를 파싱하여 하나씩 가지고 있음.
		int sustainingtime;
		String light;
		int followCurrentEdgesNumber;
		
		public Operation(Element elem) {
			//location 노드 파싱
			NodeList list = elem.getElementsByTagName("location");
			String target = ((Element)list.item(0)).getAttribute("target");
			//location type 이 edges일 경우의 처리
			if (target.compareTo("edges")==0){
				location_target = target;
				NodeList enl = ((Element)list.item(0)).getElementsByTagName("edge");
				
				//토큰으로 잘라서 edges에 하나씩 저장
				for (int i=0; i<enl.getLength(); i++){
					edges.add(enl.item(i).getTextContent());
				}
			}//follow일 경우의 처리
			else if (target.compareTo("follow-all")==0){
				location_target = target;
				location_edges = "";
			}
			
			//follow-current, follow-current(숫자)가 존재할 수 있음.
			else if (target.contains("follow-current")){
				location_target = target;
				location_edges = "";
				Pattern p = Pattern.compile("-?\\d+");
				Matcher m = p.matcher(target);
				//follow-current(숫자)일 경우
				if (m.find())
					followCurrentEdgesNumber = Integer.parseInt(m.group());
				else
					followCurrentEdgesNumber = 1;		//없으면 그냥 current나 동일함. follow-current는 follow-current1과 동일.
			}
			
			//time 노드 파싱
			list = elem.getElementsByTagName("time");
			sustainingtime = Integer.parseInt(((Element)list.item(0)).getTextContent());
			
			//light 노드 파싱
			list = elem.getElementsByTagName("light");
			light = ((Element)list.item(0)).getTextContent();
		}
		
		public String getLocation_target(){
			return location_target;
		}
		public String getLocation_edges(){
			return location_edges;
		}
		public int getSustainTime(){
			return sustainingtime;
		}
		public String getLight(){
			return light;
		}
		public ArrayList<String> getEdges(){
			return edges;
		}
		public int getFollowCurrentEdgesNumber(){
			return followCurrentEdgesNumber;
		}
	}
	
	public String getId(){
		return id;
	}
	
	public int getPriority(){
		return priority;
	}
	
	public Factor getFactor(){
		return f;
	}
	
	public Operation getOperation(){
		return o;
	}
}
