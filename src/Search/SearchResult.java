package Search;
//Lonan Hugh Lardner
//00338834
//CS4032
//P2P Web Search System
class SearchResult{
    String words; // strings matched for this url
    String[] url;   // url matching search query 
   long frequency; //number of hits for page
   
   public SearchResult(String words, String[] url, long frequency){
	   this.words = words;
	   this.url = url;
	   this.frequency = frequency;
   }
 }

