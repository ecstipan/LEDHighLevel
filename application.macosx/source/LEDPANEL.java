import processing.core.*; 
import processing.data.*; 
import processing.event.*; 
import processing.opengl.*; 

import java.io.File; 
import javax.swing.*; 
import javax.swing.filechooser.FileFilter; 
import controlP5.*; 
import java.util.Calendar; 
import java.text.SimpleDateFormat; 
import processing.serial.*; 
import java.util.concurrent.Executors; 
import java.util.concurrent.ScheduledExecutorService; 
import java.util.concurrent.ScheduledFuture; 
import java.util.concurrent.TimeUnit; 

import java.util.HashMap; 
import java.util.ArrayList; 
import java.io.File; 
import java.io.BufferedReader; 
import java.io.PrintWriter; 
import java.io.InputStream; 
import java.io.OutputStream; 
import java.io.IOException; 

public class LEDPANEL extends PApplet {








//import static java.util.concurrent.TimeUnit.SECONDS;





ControlP5 cp5;

Serial comPort;
PImage tempimage;
boolean blurring = false;
int passes = 17;
int linkCount = 0;
float blurBloom = 1.00f;
int blurSize = 2;
float v = 1.0f / ((blurSize * 2.0f + 1) * (blurSize * 2.0f + 1));

PImage bufferedImage;

int serialPortNumber = -1;

byte[][][] pixelFrame = new byte[16][16][3];

int resample_x_start = 0;
int resample_x_end = 0;
int resample_y_start = 0;
int resample_y_end = 0;

boolean running = false;
Textarea consoleTextarea;

DropdownList portChooser;

ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor();

public static final String DATE_FORMAT_NOW = "yyyy-MM-dd HH:mm:ss";

public void writeD(int test) {
  comPort.write((short)( test & 0xff)/2);
}

public void beginUploadImage() {
  if (comPort == null) {
    logError("Connect a device to upload!");
    resetConnection();
  } else {
    //write header
    logConsole("Uploading data frame...");
    comPort.clear();
    comPort.write(252);
    
    for (int y = 0; y< 16; y++) {
      for (int x = 0; x < 16 ; x++ ) {
        writeD(pixelFrame[x][y][0]);
        writeD(pixelFrame[x][y][1]);
        writeD(pixelFrame[x][y][2]);
      }
    }
    //write end of frame
    comPort.write(253);
    
    cp5.getController("serslider").setValue(75.0f);
    redraw();
    
    //check to see if we have an ack
    Runnable task = new Runnable() {
      public void run() {
        checkUploadedImage();
      }
    };
    worker.schedule(task, 1000, TimeUnit.MILLISECONDS);
  }
}

public void checkUploadedImage() {
  if (comPort.available() == 0) {
    logError("Device is not responding.");
    //resetConnection();
  } else {
    int test = comPort.read();
    if (test == 254) {
      logConsole("Successfully upload image!");
      cp5.getController("serslider").setValue(100.0f);
    } else if (test == 238) {
      logError("Image uploaded but with errors!");
      cp5.getController("serslider").setValue(0.0f);
    } else {
      logError("Failed to upload image!");
      cp5.getController("serslider").setValue(0.0f);
    }
  }
  comPort.clear();
}

public void listSerialPorts() {
  logConsole("Finding Serial Devices....");
  
  int serialCount = Serial.list().length;
  if (serialCount == 0) {
    logError("No Serial Devices Found");
    logConsole("Remember to connect a LED panel.");
  } else {
    logConsole("Found " + serialCount + " devices!");
    portChooser.clear();
    for (int i=0; i < serialCount; i++){
      portChooser.addItem(Serial.list()[i], i);
      logConsole("Found " + Serial.list()[i]);
    }
    serialPortNumber = -1;
  }
}

public void connectPanel(int serialDevice) {
  println("connecting...");
  if (serialDevice < 0 || Serial.list().length <= serialDevice) {
    logError("Invalid device ID");
    resetConnection();
  } else if (Serial.list().length == 0) {
    logError("No Serial Devices Found");
    resetConnection();
  } else {
    logConsole("Polling " + Serial.list()[serialDevice]);
    try {
      if (comPort != null) comPort.stop();
      comPort = new Serial(this, Serial.list()[serialDevice], 115200, 'N', 8, 1.0f);
    } catch (Exception e) {
      //leave blan
      logError("This device is busy!");
      resetConnection();
    }
    if (comPort == null) {
      logError("Error establishing connection.");
      resetConnection();
    } else {
      //so we've passed the connectivity test
      //now let's see if we are talking to a panel and it's active
      //send SETUP sequence
      comPort.clear();
      comPort.write(255); // setup byte
      
      //wait a second
      Runnable task = new Runnable() {
        public void run() {
          connectPartTwo();
        }
      };
      worker.schedule(task, 300, TimeUnit.MILLISECONDS);
    }
  }
}

public void resetConnection() {
  if (comPort != null) comPort.stop();
  portChooser.captionLabel().set("Select Serial Port");
  serialPortNumber = -1;
  cp5.get(Textfield.class, "deviceName").lock().setText("No Device Connected");
}

public void connectPartTwo() {
  if (comPort.available() == 0) {
    logError("Device is not responding.");
    resetConnection();
  } else {
    logConsole("Found working serial device!");
    //we have a response.. let's see if it's valid, now get the address
    if (comPort.read() != 255) {
      logError("This device is not a LED Panel.");
      resetConnection();
    } else {
      logConsole("Obtaining device ID...");
      comPort.clear();
      comPort.write(247);//address byte
      
      //wait for a response
      Runnable task = new Runnable() {
        public void run() {
          connectPartThree();
        }
      };
      worker.schedule(task, 500, TimeUnit.MILLISECONDS);
    }
  }
}

public void connectPartThree() {
  if (comPort.available() == 0) {
    logError("Device is not responding.");
    resetConnection();
  } else {
    //buffer until we get the next address
    String name = comPort.readString();
    logConsole("Device ID = " + name);
    cp5.get(Textfield.class, "deviceName").setText(name);
    logConsole("PANEL CONNECTED!");
    comPort.clear();
    logConsole("Panel is running POST");
  }
}


public static String now() {
  Calendar cal = Calendar.getInstance();
  SimpleDateFormat sdf = new SimpleDateFormat(DATE_FORMAT_NOW);
  return sdf.format(cal.getTime());
}

public void logConsole(String toadd) {
  String oldtest;
  String date = now();
  oldtest = consoleTextarea.getText();
  if (oldtest != null && oldtest.length() >0) consoleTextarea.setText(oldtest + "  \n\r CONSOLE:  " + date + ":   " + toadd);
  else consoleTextarea.setText("  CONSOLE:  " + date + ":   " + toadd);
  consoleTextarea.scroll(1.0f);
}

public void logError(String toadd) {
  String oldtest;
  String date = now();
  oldtest = consoleTextarea.getText();
  if (oldtest != null && oldtest.length() >0) consoleTextarea.setText(oldtest + "  \n\r== ERROR:  " + date + ":   " + toadd);
  else consoleTextarea.setText("==  ERROR:  " + date + ":   " + toadd);
  consoleTextarea.scroll(1.0f);
}

public int heightWrap(int x) {
  if (x <0) return tempimage.height + x-1;
  else if (x >= tempimage.height) return x - tempimage.height +1;
  else return x;
}

public int widthWrap(int x) {
  if (x < 0) return tempimage.width + x-1;
  else if (x >= tempimage.width) return x - tempimage.width +1;
  else return x;
}

public void blurImage(int pass) {
  cp5.getController("knobBlurPasses").lock();
  cp5.getController("knobBlurBloom").lock();
  cp5.getController("knobBlurRadius").lock();
  if (tempimage != null) {
    PImage blurImg;
    blurImg = createImage(tempimage.width, tempimage.height, RGB);
    tempimage.loadPixels();
    if (pass == 0) {
      linkCount = 0;
      cp5.getController("blurslider").setValue(0.0f);
      println("Starting Blur...");
    }
    // Loop through every pixel in the image
    for (int y = 0; y < tempimage.height; y++) {   // Skip top and bottom edges
      for (int x = 0; x < tempimage.width; x++) {  // Skip left and right edges
        float sum_r = 0; // Kernel sum for this pixel
        float sum_g = 0; // Kernel sum for this pixel
        float sum_b = 0; // Kernel sum for this pixel
        for (int ky = -blurSize; ky <= blurSize; ky++) {
          for (int kx = -blurSize; kx <= blurSize; kx++) {
            // Calculate the adjacent pixel for this kernel point
            int pos = heightWrap(y + ky)*tempimage.width + widthWrap(x + kx);
            
            float val_red = red(tempimage.pixels[pos]);
            float val_green = green(tempimage.pixels[pos]);
            float val_blue = blue(tempimage.pixels[pos]);
            
            sum_r += v * val_red * blurBloom;
            sum_g += v * val_green * blurBloom;
            sum_b += v * val_blue * blurBloom;
          }
        }
        // For this pixel in the new image, set the gray value
        // based on the sum from the kernel
        blurImg.pixels[y*tempimage.width + x] = color(sum_r, sum_g, sum_b);
      }
    }
    blurImg.updatePixels();
    tempimage = blurImg;
    linkCount++;
    if (linkCount >= passes) {
      blurring = false;
      println((int)Math.floor(100*(linkCount*1.0f)/(passes*1.0f)) + "%");
      println("FINISHED!");
      cp5.getController("knobBlurPasses").unlock();
      cp5.getController("knobBlurBloom").unlock();
      cp5.getController("knobBlurRadius").unlock();
      logConsole("Done.");
    } else {
      blurring = true;
      println((int)Math.floor(100*(linkCount*1.0f)/(passes*1.0f)) + "%");
      logConsole("Processing Gaussian Blur... " + (int)Math.floor(100*(linkCount*1.0f)/(passes*1.0f)) + "%");
    }
    cp5.getController("blurslider").setValue(100*(linkCount*1.0f)/(passes*1.0f));
    image(blurImg, 10, 39, 350, 350);
    redraw();
  } else {
    logError("Please load an image first!");
  }
}

// set system look and feel 
public void setup() {
  frame.setTitle("MODULAR LED PANEL ISP TEST PROGRAM");
  PFont font = createFont("arial", 12);
  size(800, 600, P2D);
  noStroke();
  frameRate(400);
  cp5 = new ControlP5(this);
  
  consoleTextarea = cp5.addTextarea("consoleLog").setPosition(10,410).setSize(780,180).setFont(createFont("arial", 12)).setLineHeight(14).setColor(color(200)).setColorBackground(color(0)).setColorForeground(color(0)).showScrollbar().setScrollForeground(color(70)).setScrollBackground(color(0));
                  
  logConsole("Welcome to LED Panel Studio");
  logConsole("Written by Rayce Stipanovich");
  logConsole("D13 Modular LED Panel Display ISP");
  logConsole("Professor Surgey Makarov");
  logConsole("Worcester Polytechnic Institute");
  logConsole("Loading UI...");
  
  cp5.addButton("loadFile").setPosition(300, 10).setSize(60, 20).setCaptionLabel("  Load Image");
  cp5.addButton("blurImage").setPosition(380, 10).setSize(60, 20).setCaptionLabel("  Blur Image");
  cp5.addTextfield("imageSelection").setPosition(10,10).setSize(280,20).setFont(font).setColor(color(200,200,200)).lock().setText("Choose an image...").setCaptionLabel("");
  cp5.addSlider("blurslider").setPosition(555, 10).setSize(235, 20).setRange(0, 100).setValue(0).setCaptionLabel("Progress").lock();
  cp5.getController("blurslider").getValueLabel().align(ControlP5.LEFT, ControlP5.BOTTOM_OUTSIDE).setPaddingX(0);
  cp5.getController("blurslider").getCaptionLabel().align(ControlP5.RIGHT, ControlP5.BOTTOM_OUTSIDE).setPaddingX(0);
  cp5.addKnob("knobBlurPasses").setRange(1,25).setValue(17).setPosition(450,10).setRadius(10).setDragDirection(Knob.HORIZONTAL).setCaptionLabel("Passes");
  cp5.addKnob("knobBlurBloom").setRange(-25,25).setValue(0).setPosition(488,10).setRadius(10).setDragDirection(Knob.HORIZONTAL).setCaptionLabel("Bloom");
  cp5.addKnob("knobBlurRadius").setRange(0,5).setValue(2).setPosition(525,10).setRadius(10).setDragDirection(Knob.HORIZONTAL).setCaptionLabel("Radius");
  cp5.getController("knobBlurPasses").getCaptionLabel().align(ControlP5.LEFT, ControlP5.BOTTOM_OUTSIDE).setPaddingX(0);
  cp5.getController("knobBlurBloom").getCaptionLabel().align(ControlP5.LEFT, ControlP5.BOTTOM_OUTSIDE).setPaddingX(0);
  
  cp5.addSlider("dsXStart").setPosition(380, 60).setSize(200, 20).setRange(1, 2).setValue(1).setCaptionLabel("X Start");
  cp5.addSlider("dsXEnd").setPosition(380, 90).setSize(200, 20).setRange(1, 17).setValue(17).setCaptionLabel("X End");
  cp5.addSlider("dsYStart").setPosition(380, 120).setSize(200, 20).setRange(1, 2).setValue(1).setCaptionLabel("Y Start");
  cp5.addSlider("dsYEnd").setPosition(380, 150).setSize(200, 20).setRange(1, 17).setValue(17).setCaptionLabel("Y End");
  
  cp5.addButton("resetResample").setPosition(380, 180).setSize(80, 20).setCaptionLabel("    Reset Sample");
  
  cp5.addButton("resampleImage").setPosition(470, 180).setSize(110, 20).setCaptionLabel("    Generate Data Frame");
  
  cp5.addTextfield("deviceName").setPosition(380,250).setSize(260,20).setFont(font).setColor(color(200,200,200)).lock().setText("No Device Connected").setCaptionLabel("");
  
  cp5.addSlider("serslider").setPosition(380, 280).setSize(260, 20).setRange(0, 100).setValue(0).setCaptionLabel("Upload Progress").lock();
  cp5.getController("serslider").getValueLabel().align(ControlP5.LEFT, ControlP5.BOTTOM_OUTSIDE).setPaddingX(0);
  cp5.getController("serslider").getCaptionLabel().align(ControlP5.RIGHT, ControlP5.BOTTOM_OUTSIDE).setPaddingX(0);
  
  cp5.addKnob("knobProgSpeed").setRange(0,255).setValue(84).setPosition(650,310).setRadius(33).setDragDirection(Knob.HORIZONTAL).setCaptionLabel("Scan Speed");
  
  portChooser = cp5.addDropdownList("portChooserD").setPosition(379, 241).setWidth(260);
  portChooser.setBackgroundColor(color(190));
  portChooser.setItemHeight(20);
  portChooser.setBarHeight(20);
  portChooser.captionLabel().set("Select Serial Port");
  portChooser.captionLabel().style().marginTop = 5;
  portChooser.captionLabel().style().marginLeft = 3;
  portChooser.valueLabel().style().marginTop = 3;
  portChooser.setValue(9999.0f);
  //ddl.scroll(0);
  portChooser.setColorActive(color(255, 128));
  
  cp5.addButton("refreshUSB").setPosition(650, 220).setSize(80, 20).setCaptionLabel("Refresh Devices").captionLabel().style().marginLeft = 2;
  cp5.addButton("connectUSB").setPosition(740, 220).setSize(50, 20).setCaptionLabel("Connect").captionLabel().style().marginLeft = 5;
  
  cp5.addButton("sendImage").setPosition(650, 280).setSize(140, 20).setCaptionLabel("Send Serial Image Data Frame").captionLabel().style().marginLeft = 5;
  
  cp5.addButton("disconnectUSB").setPosition(730, 250).setSize(60, 20).setCaptionLabel("Disconnect").captionLabel().style().marginLeft = 4;
  cp5.addButton("setNameUSB").setPosition(650, 250).setSize(70, 20).setCaptionLabel("Perform Test").captionLabel().style().marginLeft = 5;
  
  cp5.addButton("setNameUSB").setPosition(650, 250).setSize(70, 20).setCaptionLabel("Perform Test").captionLabel().style().marginLeft = 5;
  
  cp5.addButton("darkUpdate").setPosition(380, 340).setSize(125, 20).setCaptionLabel("Hide Update Status").captionLabel().style().marginLeft = 20;
  cp5.addButton("scanUpdate").setPosition(515, 340).setSize(125, 20).setCaptionLabel("Show Update Status").captionLabel().style().marginLeft = 17;
  cp5.addButton("blankOutput").setPosition(380, 370).setSize(125, 20).setCaptionLabel("Disable (Blank) Output").captionLabel().style().marginLeft = 12;
  cp5.addButton("unblankOutput").setPosition(515, 370).setSize(125, 20).setCaptionLabel("Enable (Unblank) Output").captionLabel().style().marginLeft = 10;
  
  //add tooltips
  cp5.getTooltip().setDelay(500);
  cp5.getTooltip().register("loadFile","Changes the size of the ellipse.");
  
  running = true;
  
  logConsole("UI Loaded Successfully!");
  
  //scan USB COM ports
  listSerialPorts();
  
  logConsole("READY!");
  logConsole("Please open an image file to begin.");
}

public void waitForAck() {
  comPort.clear();

  //wait for a response
  Runnable task = new Runnable() {
    public void run() {
      _waitForAck();
    }
  };
  worker.schedule(task, 200, TimeUnit.MILLISECONDS);
}

public void _waitForAck () {
  cp5.getController("darkUpdate").unlock();
  cp5.getController("scanUpdate").unlock();
  cp5.getController("unblankOutput").unlock();
  cp5.getController("blankOutput").unlock();
  if (comPort == null || comPort.available() == 0) {
    logError("Device is not responding.");
    resetConnection();
  } else {
    if (comPort.read() != 255) {
      logError("Update failed!");
    } else {
      logConsole("Done");
    }
  }
}

public void blankOutput() {
  if (comPort == null) {
    logError("No device connected!");
    resetConnection();
  } else {
    cp5.getController("darkUpdate").lock();
    cp5.getController("scanUpdate").lock();
    cp5.getController("unblankOutput").lock();
    cp5.getController("blankOutput").lock();
    logConsole("Disabling panel output...");
    comPort.write(242);
    waitForAck();
  }
}

public void unblankOutput() {
  if (comPort == null) {
    logError("No device connected!");
    resetConnection();
  } else {
    cp5.getController("darkUpdate").lock();
    cp5.getController("scanUpdate").lock();
    cp5.getController("unblankOutput").lock();
    cp5.getController("blankOutput").lock();
    logConsole("Enabling panel output...");
    comPort.write(243);
    waitForAck();
  }
}

public void darkUpdate() {
  if (comPort == null) {
    logError("No device connected!");
    resetConnection();
  } else {
    cp5.getController("darkUpdate").lock();
    cp5.getController("scanUpdate").lock();
    cp5.getController("unblankOutput").lock();
    cp5.getController("blankOutput").lock();
    logError("Setting mode to dark update...");
    comPort.write(241);
    comPort.write(0);//address byte
    waitForAck();
  }
}

public void scanUpdate() {
  if (comPort == null) {
    logError("No device connected!");
    resetConnection();
  } else {
    logError("Setting mode to scanning update...");
    comPort.write(241);
    comPort.write(65);//address byte
    waitForAck();
  }
}

public void knobProgSpeed(int val) {
  if (comPort == null) {
    logError("No device connected!");
    resetConnection();
  } else {
    comPort.write(220);//address byte
    comPort.write(val);
  }
}

public void sendImage() {
  cp5.getController("serslider").setValue(0);
  beginUploadImage();
}

public void disconnectUSB() {
  logConsole("Disconnecting Serial Device..");
  resetConnection();
  logConsole("Done.");
}

public void setNameUSB() {
  if (comPort == null) {
    logError("No device connected!");
    resetConnection();
  } else {
    logConsole("Getting Device ID");
    comPort.clear();
    comPort.write(247);//address byte
    
    //wait for a response
    Runnable task = new Runnable() {
      public void run() {
        checkDeviceID();
      }
    };
    worker.schedule(task, 500, TimeUnit.MILLISECONDS);
  }
}

public void checkDeviceID() {
  if (comPort == null || comPort.available() == 0) {
    logError("Device is not responding.");
    resetConnection();
  } else {
    String name = comPort.readString();
    logConsole("Device ID = " + name);
    cp5.get(Textfield.class, "deviceName").setText(name);
    logConsole("PANEL CONNECTED!");
    comPort.clear();
  }
}

public void controlEvent(ControlEvent theEvent) {
  if (theEvent.isGroup()) {
    String listName = theEvent.getName();
    if (listName.equals("portChooserD")) {
      serialPortNumber = (int)portChooser.getValue();
    }
  }
}

public void refreshUSB() {
  listSerialPorts();
}
public void connectUSB() {
  if (serialPortNumber < 0) {
    logError("Please Select a Serial Device!");
  } else {
    logConsole("Attempding to connect with device ID#" + serialPortNumber);
    connectPanel(serialPortNumber);
  }
}

public void resampleImage() {
  if (tempimage != null) {
    logConsole("Resampling Image...");
    
    cp5.getController("dsXStart").lock();
    cp5.getController("dsXEnd").lock();
    cp5.getController("dsYStart").lock();
    cp5.getController("dsYEnd").lock();
    
    tempimage.loadPixels();
    bufferedImage = createImage(16, 16, RGB);
    
    //generate sample points
    int xdist = (resample_x_end - resample_x_start + 1)/16;
    int ydist = (resample_y_end - resample_y_start + 1)/16;
    int xoffset = resample_x_start + xdist/2;
    int yoffset = resample_y_start + ydist/2;
    
    logConsole(" X-Axis Interpolation Distance = " + xdist);
    logConsole(" Y-Axis Interpolation Distance = " + ydist);
    logConsole(" X-Axis Offset Distance = " + xoffset);
    logConsole(" Y-Axis Offset Distance = " + yoffset);
    
    //perform multipoint sampling
    for (int y = 0; y < 16; y++) {
      logConsole("Resampling row "+ (y+1) + "...");
      redraw();
      for (int x = 0; x < 16; x++) {
        // Calculate the adjacent pixel for this kernel point
        int pos = (yoffset + y*ydist)*tempimage.width + (xoffset + x*xdist);

        pixelFrame[x][y][0] = (byte)((((short)red(tempimage.pixels[pos]) >> 7 ) & 0x01) * 255);
        pixelFrame[x][y][1] = (byte)((((short)green(tempimage.pixels[pos]) >> 7 ) & 0x01) * 255);
        pixelFrame[x][y][2] = (byte)((((short)blue(tempimage.pixels[pos]) >> 7 ) & 0x01) * 255);
      }
    }
    
    logConsole("Done.");
    logConsole("Generating Preview...");
    for (int y = 0; y < 16; y++) {
      for (int x = 0; x < 16; x++) {
        bufferedImage.pixels[y*16 + x] = color((short)(pixelFrame[x][y][0] & 0xff), (short)(pixelFrame[x][y][1] & 0xff), (short)(pixelFrame[x][y][2] & 0xff));
        logConsole("Populating pixel (" + (x+1) + ", " + (y+1) + ") with color (" + (short)(pixelFrame[x][y][0] & 0xff) + ", " + (short)(pixelFrame[x][y][1] & 0xff) + ", " + (short)(pixelFrame[x][y][2] & 0xff) + ")");
        redraw();
      }
    }
    
    bufferedImage.updatePixels();
    
    cp5.getController("dsXStart").unlock();
    cp5.getController("dsXEnd").unlock();
    cp5.getController("dsYStart").unlock();
    cp5.getController("dsYEnd").unlock();
    
    logConsole("Done.");
  } else {
    logError("Please load an image first!");
  }
}

public void resetResample() {
  if (tempimage != null) {
    logConsole("Resetting sample bounds...");
    cp5.getController("dsXStart").setMax(tempimage.width-1 - 14 );
    cp5.getController("dsXEnd").setMax(tempimage.width);
    cp5.getController("dsYStart").setMax(tempimage.height-1 - 14);
    cp5.getController("dsYEnd").setMax(tempimage.height);
    
    cp5.getController("dsXEnd").setMin(16);
    cp5.getController("dsYEnd").setMin(16);
    
    cp5.getController("dsXStart").setValue(1);
    cp5.getController("dsYStart").setValue(1);
    cp5.getController("dsXEnd").setValue(tempimage.width);
    cp5.getController("dsYEnd").setValue(tempimage.height);
    
    resample_x_start = 0;
    resample_y_start = 0;
    resample_x_end = tempimage.width-1;
    resample_y_end = tempimage.height-1;
    logConsole("Done.");
  } else {
    logError("Please load an image first!");
  }
}

public void knobBlurPasses(int theValue) {
  passes = theValue;
}
public void knobBlurRadius(int theValue) {
  blurSize = theValue;
  v = 1.0f / ((blurSize * 2.0f + 1) * (blurSize * 2.0f + 1));
}
public void knobBlurBloom(int theValue) {
  blurBloom = 1.0f + ((1.0f * theValue/25.0f)/20.0f);
}

public void dsXStart(int value) {
  if ( running ) {
    int olddifference = resample_x_end - resample_x_start;
    resample_x_start = value-1;
    
    if (cp5.getController("dsXEnd").getMin() == cp5.getController("dsXEnd").getMax()) {
      cp5.getController("dsXEnd").setMin(value + 15);
      cp5.getController("dsXEnd").setValue(value + 15);
    } else {
      cp5.getController("dsXEnd").setValue(value + olddifference);
    }
    
    cp5.getController("dsXEnd").setMin(value + 15);
    if (cp5.getController("dsXEnd").getValue() <= value + 15) {
      cp5.getController("dsXEnd").setValue(value + 16);
    }
  }
}

public void dsXEnd(int value) {
  if ( running ) {
    if (value > 0) resample_x_end = value - 1;
  }
}

public void dsYStart(int value) {
  if ( running ) {
    int olddifference = resample_y_end - resample_y_start;
    resample_y_start = value-1;
    
    if (cp5.getController("dsYEnd").getMin() == cp5.getController("dsYEnd").getMax()) {
      cp5.getController("dsYEnd").setMin(value + 15);
      cp5.getController("dsYEnd").setValue(value + 15);
    } else {
      cp5.getController("dsYEnd").setValue(value + olddifference);
    }
    
    cp5.getController("dsYEnd").setMin(value + 15);
    if (cp5.getController("dsYEnd").getValue() <= value + 15) {
      cp5.getController("dsYEnd").setValue(value + 16);
    }
  }
}

public void dsYEnd(int value) {
  if ( running ) {
    if (value > 0) resample_y_end = value - 1;
  }
}

public void openImage() {
  logConsole("Opening image...");
  try { 
    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); 
  } catch (Exception e) { 
    e.printStackTrace();  
  } 
 
  // create a file chooser 
  final JFileChooser fc = new JFileChooser(); 
 
  // in response to a button click: 
  fc.setDialogTitle("Select an Image");
  fc.setFileFilter(new FileFilter() {
        public boolean accept(File f) {
        String temp = f.getName().toLowerCase();
        return temp.endsWith(".jpg")
        || temp.endsWith(".bmp")
        || temp.endsWith(".png")
        || f.isDirectory();
      }
      public String getDescription() {
      return "Image Files";
    }
  });
  
  int returnVal = fc.showOpenDialog(this); 
 
  if (returnVal == JFileChooser.APPROVE_OPTION) { 
    File file = fc.getSelectedFile(); 
    // see if it's an image 
    // (better to write a function and check for all supported extensions)
    PImage tmp = loadImage(file.getPath()); 
    if (tmp != null) { 
      logConsole("Opening "+file.getName());
      tempimage = tmp;
      cp5.get(Textfield.class, "imageSelection").setText(""+file.getPath());
      cp5.getController("dsXStart").setMax(tmp.width-1 - 14 );
      cp5.getController("dsXEnd").setMax(tmp.width);
      cp5.getController("dsYStart").setMax(tmp.height-1 - 14);
      cp5.getController("dsYEnd").setMax(tmp.height);
      
      cp5.getController("dsXEnd").setMin(16);
      cp5.getController("dsYEnd").setMin(16);
      
      cp5.getController("dsXStart").setValue(1);
      cp5.getController("dsYStart").setValue(1);
      cp5.getController("dsXEnd").setValue(tmp.width);
      cp5.getController("dsYEnd").setValue(tmp.height);
      
      resample_x_start = 0;
      resample_y_start = 0;
      resample_x_end = tmp.width-1;
      resample_y_end = tmp.height-1;
      
      logConsole("Done.");
    } else {
      logError("Could not open file.");
    }
  } else {
    logError("Could not open file.");
  }
}

public void draw() {
  background(15, 15, 15);
  fill(0, 0, 0);
  rect(0, 0, 370, 400);
  if (tempimage != null) { 
    image(tempimage, 10, 39, 350, 350);
    noFill();
    stroke(204, 102, 0);
    strokeWeight(2);
    rect(10 + (resample_x_start*1.0f)/(tempimage.width*1.0f)*350, 39 + (resample_y_start*1.0f)/(tempimage.height*1.0f)*350, (resample_x_end*1.0f - resample_x_start*1.0f)/(tempimage.width*1.0f)*350, (resample_y_end*1.0f - resample_y_start*1.0f)/(tempimage.height*1.0f)*350);
    noStroke();
  } else {
    fill(204, 102, 0);
    rect(10, 39, 350, 350);
  }
  if (blurring) {
    blurImage(linkCount);
    System.gc();
  } else {
    linkCount = 0;
  }
  fill(30, 30, 30);
  rect(370, 50, 430, 160);
  rect(0, 400, 800, 200);
  
  if (bufferedImage != null) {
    image(bufferedImage, 650, 60, 140, 140);
  } else {
    fill(204, 102, 0);
    rect(650, 60, 140, 140);
  }
}

public void loadFile(int value) {
  openImage();
}
  static public void main(String[] passedArgs) {
    String[] appletArgs = new String[] { "LEDPANEL" };
    if (passedArgs != null) {
      PApplet.main(concat(appletArgs, passedArgs));
    } else {
      PApplet.main(appletArgs);
    }
  }
}
