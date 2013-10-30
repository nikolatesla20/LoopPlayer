package com.example.looppedal;

import java.io.File;
import java.io.FilenameFilter;
import java.io.RandomAccessFile;

import android.os.Bundle;
import android.os.Environment;
import android.app.Activity;
import android.view.Menu;
import android.view.View;
import android.widget.*;
import android.media.*;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;


public class MainActivity extends Activity {

	ListView filelist;

	Button playbutton;
	Button stopbutton;
	TextView selectedfilenametext;
	
	// we will use audiotrack to play our loop
	// because you can tell it to loop automatically.
	AudioTrack audioplayer;
	
	String selectedfilename;
	
	File rootdirectory;
	
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		filelist = (ListView)findViewById(R.id.listView1);
	
		playbutton = (Button)findViewById(R.id.button2);
		stopbutton = (Button)findViewById(R.id.button3);
		selectedfilenametext = (TextView)findViewById(R.id.textView2);
		
	
		playbutton.setOnClickListener(playclick);
		stopbutton.setOnClickListener(stopclick);
		
		// sdcard. Note I have zero error checking here
		rootdirectory = Environment.getExternalStorageDirectory();
		
		selectedfilenametext.setText("");

		// technically this should be in another thread (AsyncTask) to prevent the UI hanging, but
		// it should work fine in most cases		
		ListFiles();
			
	}

	
	@Override
	public void onDestroy()
	{
		super.onDestroy();
		StopPlaying();
		
	}
	
	
	
	private void ListFiles()
	{
		// Filter class down below, used to only show WAV files
		Filter myfilter = new Filter();
		
		String[] children = rootdirectory.list(myfilter);
		  
		ArrayAdapter<String> spinnerArrayAdapter = new ArrayAdapter<String>(this,
  		        R.layout.file_row,
  		            children);
  	  
  	  	filelist.setAdapter(spinnerArrayAdapter);
  	  	filelist.setFocusable(true);
  	  	filelist.setFocusableInTouchMode(true);
  	  	filelist.setItemsCanFocus(true);
  	  

  	  	filelist.setOnItemClickListener(listClick);
		
		
	}
	
	
    AdapterView.OnItemClickListener listClick = new AdapterView.OnItemClickListener() {
	      //@Override
	      public void onItemClick(AdapterView adapterView, View view,
	          int arg2, long arg3) {
	        
	    	  long selectedPosition = arg3;
	    	  
	    	  StopPlaying();
	    	  
	    	  selectedfilename = (String)filelist.getItemAtPosition((int)selectedPosition);
	    	  selectedfilenametext.setText(selectedfilename);
	    	  
		    	
	      }
    };	
	
	
	private void StopPlaying()
	{
		try
		{
			if (audioplayer != null)
			{
				if (audioplayer.getPlayState() == audioplayer.PLAYSTATE_PLAYING)
				{
					audioplayer.stop();
					audioplayer.release();
					audioplayer = null;
					
				}
			}
		}
		catch(Exception e)
		{
			Toast msg = Toast.makeText(MainActivity.this, "Fatal error in Stop: " + e.getMessage(), 1);
			msg.show();
		}
		
	}
	
	
	
	
	View.OnClickListener playclick = new View.OnClickListener() {
		
		@Override
		public void onClick(View v) {
			// TODO Auto-generated method stub
				
			StopPlaying();
			
			if ((selectedfilename != null) && (selectedfilename.length() > 0))
			{
				if (LoadWavFile(selectedfilename))
				{
					if (audioplayer != null)
					{
						if (audioplayer.getState() == AudioTrack.STATE_INITIALIZED)
						{
							audioplayer.play();
						}
					}
				}
			}
				
			
		}
	};
	
	
	View.OnClickListener stopclick = new View.OnClickListener() {
		
		@Override
		public void onClick(View v) {
			// TODO Auto-generated method stub
			StopPlaying();
		}
	};
	
	
	
	class Filter implements FilenameFilter  
	{  
	    public boolean accept(File dir, String name)  
	    {  
	       return (name.toLowerCase().endsWith(".wav"));
	       
	    }  
	 }  
	

	// Load a WAV file, forces format to be 44100Khz, Stereo, 16 bit.
	// Loads the WAV file into temp buffer, and then directly into an AudioTrack object set to loop
	private boolean LoadWavFile(String filenamein)
	{
		
		 try
		 {
			
			 // test here if the sample is a valid sample or not
			 File tempfile = new File(rootdirectory.getAbsolutePath() + "/" + filenamein);
			 
			 long tempfilelen = tempfile.length();
			 
			 RandomAccessFile tempfs = new RandomAccessFile(tempfile,"r");
				 
			 int TestRiff = tempfs.readByte();
			 tempfs.skipBytes(7);
			 int TestWav = tempfs.readByte();
			 
			 if ((TestRiff == 'R') && (TestWav == 'W'))
			 {
			 
				 // scan start position
				 int scanpos = 12;
				 // scan for "fmt " section to get the sample rate data
				 while (true)
				 {
					 
					tempfs.seek(scanpos);
					
					int testval = tempfs.readInt();
					
					if (testval == 0x666d7420) break;					
					
					scanpos++;
					 
				 }
	
				tempfs.skipBytes(5);
					
				 short stereoormono = tempfs.readShort();
					
				 if (stereoormono != 2)
				 {
					 selectedfilenametext.setText("Invalid file: not Stereo");
					 tempfs.close();
					 return false;
				 }
				 
				 tempfs.skipBytes(1);
				 
				 int TestRate = tempfs.readByte();
				 if (TestRate == 68)
				 {
				 
					 tempfs.skipBytes(9);
					 
					 int TestSample = tempfs.readByte();
					 
					 if (TestSample == 16)
					 {
				 		
						// scan for data start position
			    		while(true)
			    		{
			    			tempfs.seek(scanpos);
				    			
			    			int datatest = tempfs.readInt();
				    			
			    			// look for "data" string
			    			if (datatest == 0x64617461) break;
				    			
			    			scanpos++;
			    		}
			    		
			    		// get the size of WAV data we need to read
			    		ByteBuffer sizebuff = ByteBuffer.allocate(8);
			    		byte[] sizeval = new byte[4];
			    		
			    		tempfs.read(sizeval);
			    		sizebuff.put(sizeval);
			    		sizebuff.order(ByteOrder.LITTLE_ENDIAN);
			    		int datasize = sizebuff.getInt(0);
			    		
			    		// read the data...
			    		//selectedfilenametext.setText(selectedfilenametext.getText() + " : size " + new Integer(datasize).toString() + " bytes");
			    		
			    		// now read in the data
			    		// we have to go through some pain because in Java data is little endian, we need big endian
						ByteBuffer fulldata = ByteBuffer.allocate(datasize);
						byte[] data = new byte[datasize];
						tempfs.read(data);
						fulldata.put(data);
						fulldata.order(ByteOrder.LITTLE_ENDIAN);
						
						// ok now we can create an array of shorts (16 bit data) and load it
						// we are stereo 16 bit, so each sample is 2 bytes
						short[] sounddata = new short[datasize/2];
						
						// copy data from ByteBuffer into our short buffer. Short buffer is used
						// to load the AudioTrack object
						int totalsamples = datasize/2;
						for (int counter1 = 0; counter1 < totalsamples;counter1++)
			    		{
			    			sounddata[counter1] = fulldata.getShort(counter1*2);
			    			
			    		}
						
						// "frames" are two full samples (Because of stereo), so datasize/4
						int totalnumberframes = datasize/4;
						
						// create the audio track, load it, play it
						audioplayer = new AudioTrack(AudioManager.STREAM_MUSIC,44100,AudioFormat.CHANNEL_OUT_STEREO,AudioFormat.ENCODING_PCM_16BIT,datasize,AudioTrack.MODE_STATIC);
						
						audioplayer.write(sounddata, 0, datasize/2);
						
						// this will cause a seamless loop
						audioplayer.setLoopPoints(0, totalnumberframes, -1);
							    		
						 
						tempfs.close();
						
						return true;
						 
						 
					 } else
					 {
						 selectedfilenametext.setText("Invalid file: not 16 bit");
						 tempfs.close();
						 return false;
					 }
				 
				 } else
				 {
					 selectedfilenametext.setText("Invalid file: not 44100 sample rate");
					 tempfs.close();
					 return false;
					 
				 }
				
			 
			 } else
			 {
				 selectedfilenametext.setText("Invalid file: Not a WAV file");
				 tempfs.close();
				 return false;
			 }
		 }
		 catch(Exception i)
		 {
			 
			 return false;
		 }
	
		
	}
	
	
	
}
