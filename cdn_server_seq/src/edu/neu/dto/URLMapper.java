package edu.neu.dto;

public class URLMapper {
	
	private String contentPath;
	private long size;
	private String lruCount;
	private long lruTime;
	
	public long getLruTime() {
		return lruTime;
	}
	public void setLruTime(long lruTime) {
		this.lruTime = lruTime;
	}
	public String getContentPath() {
		return contentPath;
	}
	public void setContentPath(String content) {
		this.contentPath = content;
	}
	public long getSize() {
		return size;
	}
	public void setSize(long size) {
		this.size = size;
	}
	public String getLruCount() {
		return lruCount;
	}
	public void setLruCount(String lruCount) {
		this.lruCount = lruCount;
	}
	
	

}
