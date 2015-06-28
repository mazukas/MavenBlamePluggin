package gov.sbs.blame;

public class PmdViolation {
	private String fileName;
	private int start;
	private int end;
	private String rule;
	private String msg;
	private String violator;
	
	public PmdViolation() {

	}
	
	public PmdViolation(String fileName, int start, int end, String rule, String msg) {
		this.fileName = fileName;
		this.start = start;
		this.end = end;
		this.rule = rule;
		this.msg = msg;
	}

	public int getStart() {
		return start;
	}

	public void setStart(int start) {
		this.start = start;
	}

	public int getEnd() {
		return end;
	}

	public void setEnd(int end) {
		this.end = end;
	}

	public String getRule() {
		return rule;
	}

	public void setRule(String rule) {
		this.rule = rule;
	}

	public String getMsg() {
		return msg;
	}

	public void setMsg(String msg) {
		this.msg = msg;
	}
	

	public String getFileName() {
		return fileName;
	}

	public void setFileName(String fileName) {
		this.fileName = fileName;
	}
	
	public String getViolator() {
		return violator;
	}

	public void setViolator(String violator) {
		this.violator = violator;
	}
	
	public String toString() {
		StringBuffer sb = new StringBuffer();
		sb.append("File Name : " + getFileName());
		sb.append("\nStart Index : " + getStart());
		sb.append("\nEnd Index : " + getEnd());
		sb.append("\nRule : " + getRule());
		sb.append("\nMessage : " + getMsg());
		sb.append("\nViolator : " + getViolator());
		return sb.toString();
	}

}
