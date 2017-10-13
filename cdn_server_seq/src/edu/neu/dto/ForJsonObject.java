package edu.neu.dto;


import java.util.Map;

public class ForJsonObject {
	
	private Map<String, URLMapper> allLinks;
	private  long fullSize;

	public Map<String, URLMapper> getAllLinks() {
		return allLinks;
	}

	public void setAllLinks(Map<String, URLMapper> allLinks) {
		this.allLinks = allLinks;
	}

	public long getFullSize() {
		return fullSize;
	}

	public void setFullSize(long fullSize) {
		this.fullSize = fullSize;
	}
	
	

}
