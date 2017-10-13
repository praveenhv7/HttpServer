package edu.neu.comparators;

import java.util.Comparator;
import java.util.Map;
import java.util.Map.Entry;

import edu.neu.dto.URLMapper;

public class LRUComparator implements Comparator<Entry<String,URLMapper>>{

	
	@Override
	/**
	 * Comparator is used to sort the map contents so that removal of files takes place
	 * with the highest lru value file.
	 */
	public int compare(Entry<String, URLMapper> o1, Entry<String, URLMapper> o2) {
		// 
		URLMapper mapper1=o1.getValue();
		URLMapper mapper2=o2.getValue();
		
		Long lruCount1=Long.parseLong(mapper1.getLruCount());
		Long lruCount2=Long.parseLong(mapper2.getLruCount());
		
		Long lruTime1=mapper1.getLruTime();
		Long lruTime2=mapper2.getLruTime();
		int difference=0;
		
		if(lruCount1==lruCount2)
		{
			difference=(int) (lruTime1-lruTime2);
		}
		else
			difference=(int) (lruCount1-lruCount2);
		
		return difference;
	}

}
