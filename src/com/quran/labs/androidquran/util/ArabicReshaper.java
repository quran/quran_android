/**
 * @author Rehab Mohammed
 * Idea of the ARABIC_GLYPHS matrix by Ahmed Essam
 */ 

package com.quran.labs.androidquran.util;

import java.util.HashMap;


public class ArabicReshaper {
	
	public static final char RTL = '\u200f';
	
	/**
	 * States of the word
	 */
	private final  int NON_ARABIC =0, ISOLATED = 1, BEGINING = 2, MIDDLE = 3, END = 4, ARABIC2FORM=5, LAMALEF=6;
	/**
	 * Previous state of the word
	 */
	private int preState = NON_ARABIC;
	/**
	 * Number of forms the word has
	 */
	@SuppressWarnings("unused")
	private int forms = 0;
	/**
	 * Location of the current char in the ARABIC_GLYPHS matrix
	 */
	private int loc = 0 ;
	/**
	 * States for lam alef
	 */
	private int lamState=0 ,alef=0;
	/**
	 * The current and next chars
	 */
	private char curntChar, nxtChar;
	/**
	 * To decide either stay in lam alef state or change the state
	 */
	private boolean lam = true, append = false;
	
	/**
	 * used to handle numbers switch
	 */
	private boolean number = false, flipNumber = false;
	private int numStartIndex = -1;
	
	/**
	 * The Arabic chars unicode mapping matrix	
	 * char, isolated, beginning, middle, last
	 */
	private  char[][] ARABIC_GLYPHS = {
	{ 1570, 65153, 65153, 65154, 65154, 2 },
	{ 1571, 65155, 65155, 65156, 65156, 2 },
	{ 1572, 65157, 65157, 65158, 65158, 2 },
	{ 1573, 65159, 65159, 65160, 65160, 2 },
	{ 1574, 65161, 65163, 65164, 65162, 4 },
	{ 1575, 65165, 65165, 65166, 65166, 2 },
	{ 1576, 65167, 65169, 65170, 65168, 4 },
	{ 1577, 65171, 65171, 65172, 65172, 2 },
	{ 1578, 65173, 65175, 65176, 65174, 4 },
	{ 1579, 65177, 65179, 65180, 65178, 4 },
	{ 1580, 65181, 65183, 65184, 65182, 4 },
	{ 1581, 65185, 65187, 65188, 65186, 4 },
	{ 1582, 65189, 65191, 65192, 65190, 4 },
	{ 1583, 65193, 65193, 65194, 65194, 2 },
	{ 1584, 65195, 65195, 65196, 65196, 2 },
	{ 1585, 65197, 65197, 65198, 65198, 2 },
	{ 1586, 65199, 65199, 65200, 65200, 2 },
	{ 1587, 65201, 65203, 65204, 65202, 4 },
	{ 1588, 65205, 65207, 65208, 65206, 4 },
	{ 1589, 65209, 65211, 65212, 65210, 4 },
	{ 1590, 65213, 65215, 65216, 65214, 4 },
	{ 1591, 65217, 65219, 65218, 65220, 4 },
	{ 1592, 65221, 65223, 65222, 65222, 4 },
	{ 1593, 65225, 65227, 65228, 65226, 4 },
	{ 1594, 65229, 65231, 65232, 65230, 4 },
	{ 1601, 65233, 65235, 65236, 65234, 4 },
	{ 1602, 65237, 65239, 65240, 65238, 4 },
	{ 1603, 65241, 65243, 65244, 65242, 4 },
	{ 1604, 65245, 65247, 65248, 65246, 4 },
	{ 1605, 65249, 65251, 65252, 65250, 4 },
	{ 1606, 65253, 65255, 65256, 65254, 4 },
	{ 1607, 65257, 65259, 65260, 65258, 4 },
	{ 1608, 65261, 65261, 65262, 65262, 2 },
	{ 1609, 65263, 65265, 65266, 65264, 4 },
	{ 1610, 65265, 65267, 65268, 65266, 4 } };
	
	/**
	 * The different types of alef
	 */
	private  char [][] LAM_ALEF = {
			{ 65269, 65270 },
			{ 65271, 65272 },
			{ 65273, 65274 },
			{ 65275, 65276 }
	};
	
	private HashMap<Character, Character> arabicNumbers = new HashMap<Character, Character>();
	
	public ArabicReshaper() {
		arabicNumbers.put(new Character('0'), new Character('\u0660'));
		arabicNumbers.put(new Character('1'), new Character('\u0661'));
		arabicNumbers.put(new Character('2'), new Character('\u0662'));
		arabicNumbers.put(new Character('3'), new Character('\u0663'));
		arabicNumbers.put(new Character('4'), new Character('\u0664'));
		arabicNumbers.put(new Character('5'), new Character('\u0665'));
		arabicNumbers.put(new Character('6'), new Character('\u0666'));
		arabicNumbers.put(new Character('7'), new Character('\u0667'));
		arabicNumbers.put(new Character('8'), new Character('\u0668'));
		arabicNumbers.put(new Character('9'), new Character('\u0669'));
	}
	
	/**
	 * Takes the text and iterates over its chars then send them to the FSM
	 * to compute the state and choose the correct form for each char
	 * @param text: the text to be reshaped
	 * @return the reshaped text
	 */
	public  String reshape(String text){
		int state=0;StringBuffer txt=new StringBuffer("");
		if(text==""||text==null)
			return "";
		// Append an empty char to the text to avoid repeating code after loop
		text = text + " ";
		for (int i = 0 ; i < text.length()-1 ; i++){
			curntChar = text.charAt(i);
			nxtChar = text.charAt(i+1);
			state = getCurrentState(curntChar,nxtChar);
			if (number && numStartIndex == -1) {
				numStartIndex = i;
			}
			if (flipNumber) {
				number = false;
				flipNumber = false;
				String num = text.substring(numStartIndex, i + 1);
				// if the whole text is a number don't reverse it
				txt.append(convertToArabicNumbers(num, !(numStartIndex == 0 && (i + 2) == text.length())));
				numStartIndex = -1;
			} else if (!append && !number)
				txt .append(getReshapedChar(state));
			else
				append = false;
		}
		return txt.toString();
	}
	
	private String convertToArabicNumbers(String num, boolean reverse) {
		StringBuffer sb = new StringBuffer();
		for (int i = 0; i < num.length(); i++)
			sb.append(arabicNumbers.get(num.charAt(i)));
		return (reverse ? sb.reverse().toString() : sb.toString()) + RTL;
	}

	/**
	 * Computes the state of the FSM using the current and next chars
	 * @param current: the current char
	 * @param next: the next char
	 * @return the state of the FSM
	 */
	private  int getCurrentState(char current, char next){
		int state=0, curnt=0, nxt=0;
		curnt=isArabicChar(current);
		nxt=isArabicChar(next);
		loc = curnt;
		switch (preState){
		case NON_ARABIC:
			if(curnt==-1 )
				state = NON_ARABIC;
			else if(curnt==28 && (nxt==0 ||nxt==1 ||nxt==3||nxt==5 ))
				state = LAMALEF;
			else if(curnt!=-1 && nxt==-1)
				state = ISOLATED;
			else if(curnt!=-1 && nxt!=-1){
				if (ARABIC_GLYPHS[curnt][5]==2)
					state = ARABIC2FORM;
				else
					state = BEGINING;
			}	
			lamState = BEGINING;
			
			number = Character.isDigit(current);
			flipNumber = number && !Character.isDigit(next);
			break;
		case ISOLATED:
			if(curnt==-1 )
				state = NON_ARABIC;	
			break;
		case BEGINING:
			if(curnt==28 && (nxt==0 ||nxt==1 ||nxt==3 ))
				state = LAMALEF;
			else if(curnt!=-1 ){
				if (ARABIC_GLYPHS[curnt][5]==2)
					state = END;
				else {
					if ( nxt!=-1)
						state = MIDDLE;
					else if ( nxt==-1)
						state = END;
				}				
			}		
			lamState = END;
			break;
		case MIDDLE:
			if(curnt==28 && (nxt==0 ||nxt==1 ||nxt==3||nxt==5 ))
				state = LAMALEF;
			else if(curnt!=-1 ){
				if (ARABIC_GLYPHS[curnt][5]==2)
					state = END;
				else {
					if ( nxt!=-1)
						state = MIDDLE;
					else if ( nxt==-1)
						state = END;
				}				
			}	
			lamState = END;
			break;
		case END:
			if(curnt==-1 )
				state = NON_ARABIC;
			else if(curnt==28 && (nxt==0 ||nxt==1 ||nxt==3||nxt==5 ))
				state = LAMALEF;
			else if(curnt!=-1 && nxt==-1)
				state = ISOLATED;
			else if(curnt!=-1 && nxt!=-1){
				if (ARABIC_GLYPHS[curnt][5]==2)
					state = ARABIC2FORM;
				else
					state = BEGINING;
			}
			lamState = BEGINING;
			break;
		case ARABIC2FORM:
			if(curnt==-1 )
				state = NON_ARABIC;
			else if(curnt==28 && (nxt==0 ||nxt==1 ||nxt==3||nxt==5 ))
				state = LAMALEF;
			else if(curnt!=-1){
				if (ARABIC_GLYPHS[curnt][5]==2)
					state = ARABIC2FORM;
				else{
					if(nxt==-1)
						state = ISOLATED;
					else
						state = BEGINING;
				}
			}
			lamState = BEGINING;
			break;
		case LAMALEF:
			if (lam){
				lam = false;
			}
			else{
				if(curnt==-1 )
					state = NON_ARABIC;
				else if(curnt==28 && (nxt==0 ||nxt==1 ||nxt==3||nxt==5 ))
					state = LAMALEF;
				else if(curnt!=-1 && nxt==-1)
					state = ISOLATED;
				else if(curnt!=-1 && nxt!=-1){
					if (ARABIC_GLYPHS[curnt][5]==2)
						state = ARABIC2FORM;
					else
						state = BEGINING;
					}	
				lam = true;
			}
			lamState = BEGINING;
			break;

		}
		preState = state;
		alef = nxt;
		return state;
		
	}
	
	/**
	 * Checks if the char is Arabic char
	 * @param target: the char to check if Arabic char
	 * @return the char location in the ARABIC_GLYPHS matrix if the char is Arabic
	 * and -1 if not Arabic char
	 */
	private  int isArabicChar(char target){
		for (int i = 0 ; i < 35 ; i++){
			if (target == ARABIC_GLYPHS[i][0]){
				return i;
			}
		}
		return -1;
	}
	
	/**
	 * Gets the reshaped char depends on the FSM state
	 * @param state: the state of the FSM
	 * @return the reshaped char
	 */
	private  char getReshapedChar(int state){
		char reshaped = 0 ;
		switch(state){
		case NON_ARABIC:
			if (Character.isDigit(curntChar))
				reshaped = arabicNumbers.get(new Character(curntChar));
			else
				reshaped = curntChar;
			break;
		case ISOLATED:
			reshaped = ARABIC_GLYPHS[loc][1];
			break;
		case BEGINING:
			reshaped = ARABIC_GLYPHS[loc][2];
			break;
		case MIDDLE:
			reshaped = ARABIC_GLYPHS[loc][3];
			break;
		case END:
			reshaped = ARABIC_GLYPHS[loc][4];
			break;
		case ARABIC2FORM:
			reshaped = ARABIC_GLYPHS[loc][1];
			break;
		case LAMALEF:
				append = true;
			if (lamState == BEGINING){
				if (alef==0)
					reshaped = LAM_ALEF[0][0];
				else if (alef==1)
					reshaped = LAM_ALEF[1][0];
				else if (alef==3)
					reshaped = LAM_ALEF[2][0];
				else if (alef==5)
					reshaped = LAM_ALEF[3][0];				
			}
			else{
				if (alef==0)
					reshaped = LAM_ALEF[0][1];
				else if (alef==1)
					reshaped = LAM_ALEF[1][1];
				else if (alef==3)
					reshaped = LAM_ALEF[2][1];
				else if (alef==5)
					reshaped = LAM_ALEF[3][1];				
			}
			break;
		}
		return reshaped;
	}
} 
