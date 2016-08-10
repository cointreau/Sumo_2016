import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;


public class Policy {

	//예약어
	private final String NV = "NV";
	private final String AW = "AW";

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
		ArrayList<String> edges_l;		//string 형태의 location edges를 파싱하여 하나씩 가지고 있음.
		HashMap<String, String> edges_l_type;		//같은 인덱스 상의 edges_l의 예약어 타입(NV,AW를 가짐) (edge명, 타입)
		ArrayList<String> edges_r;		//string 형태의 location edges를 파싱하여 하나씩 가지고 있음.
		HashMap<String, String> edges_r_type;		//같은 인덱스 상의 edges_r의 예약어 타입(NV,AW를 가짐) (edge명, 타입)
		String vehicle_target;			//all, ambulance
		double vehicle_number_l;
		double vehicle_number_r;
		String formula_l;
		String formula_r;
		String vehicle_number_sign;		//GE, G, E, LE, L 
		int time;

		public Factor(Element elem) {
			//location 노드 파싱(formula까지 파싱해야함)
			NodeList list = elem.getElementsByTagName("location");
			String target = ((Element)list.item(0)).getAttribute("target");
			//location type 이 edges일 경우의 처리
			if (target.compareTo("edges")==0){
				location_target = target;
				NodeList enl = ((Element)list.item(0)).getElementsByTagName("formula");

				for (int i=0; i<enl.getLength(); i++){
					Element e = ((Element)enl.item(i));
					String side = e.getAttribute("side"); 
					if (side.compareToIgnoreCase("left")==0)
						formula_l = e.getTextContent();
					else if (side.compareToIgnoreCase("operator")==0)
						vehicle_number_sign = e.getTextContent();
					else if (side.compareToIgnoreCase("right")==0)
						formula_r = e.getTextContent();
				}

				//edges_l 파싱
				if (isNumeric(formula_l))				//숫자만 있을 때는.
					vehicle_number_l = Double.parseDouble(formula_r);
				else{
					edges_l = new ArrayList<String>();
					edges_l_type = new HashMap<String, String>();

					// NV 부터
					Matcher m = Pattern.compile("NV\\((.*?)\\)").matcher(formula_l);
					while (m.find()){
						edges_l.add(m.group(1));
						if (edges_l_type.containsKey(m.group(1)))		//이미 가지고 있으면 (그럴리 없지만)
							edges_l_type.put(m.group(1), "AWNV");
						else
							edges_l_type.put(m.group(1), "NV");
					}

					// 다음 AW
					m = Pattern.compile("AW\\((.*?)\\)").matcher(formula_l);
					while (m.find()){
						edges_l.add(m.group(1));
						if (edges_l_type.containsKey(m.group(1)))		//이미 가지고 있으면
							edges_l_type.put(m.group(1), "AWNV");
						else
							edges_l_type.put(m.group(1), "AW");
					}
				}

				//edges_r 파싱(만약 그냥 숫자이면 vehicle_number에 넣어버림
				if (isNumeric(formula_r))
					vehicle_number_r = Double.parseDouble(formula_r);

				else{
					edges_r = new ArrayList<String>();
					edges_r_type = new HashMap<String, String>();

					// NV 부터
					Matcher m = Pattern.compile("NV\\((.*?)\\)").matcher(formula_r);
					while (m.find()){
						edges_r.add(m.group(1));
						if (edges_r_type.containsKey(m.group(1)))		//이미 가지고 있으면 (그럴리 없지만)
							edges_r_type.put(m.group(1), "AWNV");
						else
							edges_r_type.put(m.group(1), "NV");
					}

					// 다음 AW
					m = Pattern.compile("AW\\((.*?)\\)").matcher(formula_r);
					while (m.find()){
						edges_r.add(m.group(1));
						if (edges_r_type.containsKey(m.group(1)))		//이미 가지고 있으면 
							edges_r_type.put(m.group(1), "AWNV");
						else
							edges_r_type.put(m.group(1), "AW");
					}
				}
			}

			//all일 경우의 처리
			else if (target.compareTo("all")==0){
				location_target = target;
				
				NodeList enl = ((Element)list.item(0)).getElementsByTagName("formula");

				for (int i=0; i<enl.getLength(); i++){
					Element e = ((Element)enl.item(i));
					String side = e.getAttribute("side"); 
					if (side.compareToIgnoreCase("left")==0)
						formula_l = e.getTextContent();
					else if (side.compareToIgnoreCase("operator")==0)
						vehicle_number_sign = e.getTextContent();
					else if (side.compareToIgnoreCase("right")==0)
						formula_r = e.getTextContent();
				}

				//edges_l 파싱
				if (isNumeric(formula_l))
					vehicle_number_l = Double.parseDouble(formula_r);
				else{
					edges_l = new ArrayList<String>();
					edges_l_type = new HashMap<String, String>();

					// NV 부터
					Matcher m = Pattern.compile("NV\\((.*?)\\)").matcher(formula_l);
					while (m.find()){
						edges_l.add(m.group(1));
						if (edges_l_type.containsKey(m.group(1)))		//이미 가지고 있으면 (그럴리 없지만)
							edges_l_type.put(m.group(1), "AWNV");
						else
							edges_l_type.put(m.group(1), "NV");
					}

					// 다음 AW
					m = Pattern.compile("AW\\((.*?)\\)").matcher(formula_l);
					while (m.find()){
						edges_l.add(m.group(1));
						if (edges_l_type.containsKey(m.group(1)))		//이미 가지고 있으면
							edges_l_type.put(m.group(1), "AWNV");
						else
							edges_l_type.put(m.group(1), "AW");
					}
				}

				//edges_r 파싱(만약 그냥 숫자이면 vehicle_number에 넣어버림
				if (isNumeric(formula_r))
					vehicle_number_r = Double.parseDouble(formula_r);

				else{
					edges_r = new ArrayList<String>();
					edges_r_type = new HashMap<String, String>();

					// NV 부터
					Matcher m = Pattern.compile("NV\\((.*?)\\)").matcher(formula_r);
					while (m.find()){
						edges_r.add(m.group(1));
						if (edges_r_type.containsKey(m.group(1)))		//이미 가지고 있으면 (그럴리 없지만)
							edges_r_type.put(m.group(1), "AWNV");
						else
							edges_r_type.put(m.group(1), "NV");
					}

					// 다음 AW
					m = Pattern.compile("AW\\((.*?)\\)").matcher(formula_r);
					while (m.find()){
						edges_r.add(m.group(1));
						if (edges_r_type.containsKey(m.group(1)))		//이미 가지고 있으면 
							edges_r_type.put(m.group(1), "AWNV");
						else
							edges_r_type.put(m.group(1), "AW");
					}
				}
			}

			//vehicle 노드 파싱
			list = elem.getElementsByTagName("vehicle");
			vehicle_target = ((Element)list.item(0)).getAttribute("target");

			//time 노드 파싱
			list = elem.getElementsByTagName("time");
			time = Integer.parseInt(((Element)list.item(0)).getTextContent());

		}

		public String getLocation_target(){
			return location_target;
		}

		public String getVehicle_target(){
			return vehicle_target;
		}
		
		public double getVehicle_number_l(){
			return vehicle_number_l;
		}
		
		public double getVehicle_number_r(){
			return vehicle_number_r;
		}

		public ArrayList<String> getEdges_l(){
			return edges_l;
		}

		public HashMap<String, String> getEdges_l_type(){
			return edges_l_type;
		}

		public ArrayList<String> getEdges_r(){
			return edges_r;
		}

		public HashMap<String, String> getEdges_r_type(){
			return edges_r_type;
		}
		
		public String getTypesOfEdges(String edge, String side){
			if (side.compareToIgnoreCase("l")==0)			//left side
				return edges_l_type.get(edge);
			else 			//right side
				return edges_r_type.get(edge);
		}

		public String getVehicle_number_sign(){
			return vehicle_number_sign;
		}
		public int getTime(){
			return time;
		}
		
		public String getFormula_l(){
			return formula_l;
		}
		
		public String getFormula_r(){
			return formula_r;
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
			}
			//all 일 경우의 처리.
			else if (target.compareTo("all")==0){
				location_target = target;
			}
			//follow일 경우의 처리
			else if (target.compareTo("follow-all")==0){
				location_target = target;
				location_edges = "";
				if (getFactor().getVehicle_target().compareTo("all")==0){		//만약 target이 specific하지 않으면, 그냥 all이랑 똑같이 함.
					location_target = "all";
				}
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
				if (getFactor().getVehicle_target().compareTo("all")==0){		//만약 target이 specific하지 않으면, 그냥 all이랑 똑같이 함.
					location_target = "all";
				}
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



	public boolean isNumeric(String str)  
	{  
		try  
		{  
			double d = Double.parseDouble(str);  
		}  
		catch(NumberFormatException nfe)  
		{  
			return false;  
		}  
		return true;  
	}
}
