package com.miemie.appmanager;

import com.miemie.appmanager.Api.DroidApp;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;

public class AppComparator implements java.util.Comparator<DroidApp>{

	private static final String ENGLISH = "english";
	private static final String CHINESE = "chinese";
	
	private static final String TAG = AppComparator.class.getSimpleName();
	
	final static int GB_SP_DIFF = 160;
	// 存放国标一级汉字不同读音的起始区位码
	final static int[] secPosValueList = { 1601, 1637, 1833, 2078, 2274, 2302,
			2433, 2594, 2787, 3106, 3212, 3472, 3635, 3722, 3730, 3858,
			4027, 4086, 4390, 4558, 4684, 4925, 5249, 5600 };
	// 存放国标一级汉字不同读音的起始区位码对应读音
	final static char[] firstLetter = { 'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h',
			'j', 'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't', 'w',
			'x', 'y', 'z' };
	
	@Override
	public int compare(DroidApp lhs, DroidApp rhs) {
		String name1 = lhs.toString();
		String name2 = rhs.toString();
		String substring1 = name1.substring(0, 1);
		String substring2 = name2.substring(0, 1);

		if (lhs.used != rhs.used) {
			if (lhs.used)
				return 1;
			if (rhs.used)
				return 1;
		}
		
		String judge1 = judge(substring1);
		String judge2 = judge(substring2);
		if (judge1.equals(CHINESE) && judge2.equals(CHINESE)) {
			String firstLetter1 = getSpells(name1);
			String firstLetter2 = getSpells(name2);
			name1 = firstLetter1;
			name2 = firstLetter2;
		}
		return name1.compareToIgnoreCase(name2);
	}

	// judge chinese or english
	private String judge(String strNameCode) {
		if (strNameCode != null && !strNameCode.equals("")) {
			String strNameCodeString = "";
			try {
				if ("%".equals(strNameCode)) {
					return "";
				}
				strNameCodeString = URLDecoder.decode(strNameCode, "utf-8");
			} catch (UnsupportedEncodingException e) {
			}
			char[] strChar = strNameCodeString.toCharArray();
			char strCharString = 'F';
			for (char strChars : strChar) {
				strCharString = strChars;
			}
			if (strCharString >= 0x0391 && strCharString <= 0xFFE5) {
				return CHINESE;
			}
			if (strCharString >= 0x0000 && strCharString <= 0x00FF) {
				return ENGLISH;
			}
		}
		return "";

	}
	
	private static String getSpells(String characters) {
		StringBuffer buffer = new StringBuffer();
		for (int i = 0; i < characters.length(); i++) {
			char ch = characters.charAt(i);
			if ((ch >> 7) == 0) {
				// 判断是否为汉字，如果左移7为为0就不是汉字，否则是汉字
			} else {
				char spell = getFirstLetter(ch);
				buffer.append(String.valueOf(spell));
			}
		}
		return buffer.toString();
	}
	
	private static Character getFirstLetter(char ch) {
		byte[] uniCode = null;
		try {
			uniCode = String.valueOf(ch).getBytes("GBK");
		} catch (UnsupportedEncodingException e) {
			return null;
		}
		if (uniCode.length < 2) {
			return ch;
		}
		return convert(uniCode);
	}

	/**
     * 获取一个汉字的拼音首字母。 GB码两个字节分别减去160，转换成10进制码组合就可以得到区位码
     * 例如汉字“你”的GB码是0xC4/0xE3，分别减去0xA0（160）就是0x24/0x43
     * 0x24转成10进制就是36，0x43是67，那么它的区位码就是3667，在对照表中读音为‘n’
     */
	private static Character convert(byte[] bytes) {
		char result = '-';
		int secPosValue = 0;
		int i;
		for (i = 0; i < bytes.length; i++) {
			bytes[i] -= GB_SP_DIFF;
		}
		secPosValue = bytes[0] * 100 + bytes[1];

		for (i = 0; i < 23; i++) {
			if (secPosValue >= secPosValueList[i] && secPosValue < secPosValueList[i + 1]) {
				result = firstLetter[i];
				break;
			}
		}

		return result;
	} 
	
}
