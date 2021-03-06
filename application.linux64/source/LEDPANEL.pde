import java.io.File;
import javax.swing.*;
import javax.swing.filechooser.FileFilter;
import controlP5.*;
import java.util.Calendar;
import java.text.SimpleDateFormat;
import processing.serial.*;

ControlP5 cp5;

Serial comPort;
PImage tempimage;
boolean blurring = false;
int passes = 17;
int linkCount = 0;
float blurBloom = 1.00;
int blurSize = 2;
float v = 1.0 / ((blurSize * 2.0 + 1) * (blurSize * 2.0 + 1));

PImage bufferedImage;

byte[][][] pixelFrame = new byte[16][16][3];

int resample_x_start = 0;
int resample_x_end = 0;
int resample_y_start = 0;
int resample_y_end = 0;

boolean running = false;
Textarea consoleTextarea;

public static final String DATE_FORMAT_NOW = "yyyy-MM-dd HH:mm:ss";

void listSerialPorts() {
  logConsole("Finding Serial Devices....");
  
  int serialCount = Serial.list().length;
  if (serialCount == 0) {
    logError("No Serial Devices Found");
    logConsole("Remember to connect a LED panel.");
  } else {
    logConsole("Found " + serialCount + " devices!");
    for (int i=0; i < serialCount; i++){
      logConsole("Found " + Serial.list()[i]);
    }
  }
}

void connectPanel(int serialDevice) {
  if (serialDevice < 0 || Serial.list().length >= serialDevice) {
    logError("Invalid device ID");
  } else if (Serial.list().length == 0) {
    logError("No Serial Devices Found");
  } else {
    comPort = new Serial(this, Serial.list()[serialDevice], 115200);
    if (comPort == null) {
      logError("Error establishing connection.");
    } else {
      //so we've passed the connectivity test
      //now let's see if we are talking to a panel and it's active
      //send SETUP sequence
      comPort.write(0xff); // setup byte
      
      //wait a second
      try {
        Thread.sleep(1000);
      } catch(InterruptedException ex) {
        Thread.currentThread().interrupt();
      }
      if (comPort.available() == 0) {
        logError("Device is not responding.");
      } else {
        //we have a response.. let's see if it's valid, now get the address
        if (comPort.read() == 0xff) {
          logError("This device is not a LED Panel.");
        } else {
          comPort.write(0xF7);//address byte
          
          //wait for a response
          
          
        }
      }
    }
  }
}

public static String now() {
  Calendar cal = Calendar.getInstance();
  SimpleDateFormat sdf = new SimpleDateFormat(DATE_FORMAT_NOW);
  return sdf.format(cal.getTime());
}

void logConsole(String toadd) {
  String oldtest;
  String date = now();
  oldtest = consoleTextarea.getText();
  if (oldtest != null && oldtest.length() >0) consoleTextarea.setText(oldtest + "  \n\r CONSOLE:  " + date + ":   " + toadd);
  else consoleTextarea.setText("  CONSOLE:  " + date + ":   " + toadd);
  consoleTextarea.scroll(1.0);
}

void logError(String toadd) {
  String oldtest;
  String date = now();
  oldtest = consoleTextarea.getText();
  if (oldtest != null && oldtest.length() >0) consoleTextarea.setText(oldtest + "  \n\r== ERROR:  " + date + ":   " + toadd);
  else consoleTextarea.setText("==  ERROR:  " + date + ":   " + toadd);
  consoleTextarea.scroll(1.0);
}

int heightWrap(int x) {
  if (x <0) return tempimage.height + x-1;
  else if (x >= tempimage.height) return x - tempimage.height +1;
  else return x;
}

int widthWrap(int x) {
  if (x < 0) return tempimage.width + x-1;
  else if (x >= tempimage.width) return x - tempimage.width +1;
  else return x;
}

void blurImage(int pass) {
  cp5.getController("knobBlurPasses").lock();
  cp5.getController("knobBlurBloom").lock();
  cp5.getController("knobBlurRadius").lock();
  if (tempimage != null) {
    PImage blurImg;
    blurImg = createImage(tempimage.width, tempimage.height, RGB);
    tempimage.loadPixels();
    if (pass == 0) {
      linkCount = 0;
      cp5.getController("blurslider").setValue(0.0);
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
      println((int)Math.floor(100*(linkCount*1.0)/(passes*1.0)) + "%");
      println("FINISHED!");
      cp5.getController("knobBlurPasses").unlock();
      cp5.getController("knobBlurBloom").unlock();
      cp5.getController("knobBlurRadius").unlock();
      logConsole("Done.");
    } else {
      blurring = true;
      println((int)Math.floor(100*(linkCount*1.0)/(passes*1.0)) + "%");
      logConsole("Processing Gaussian Blur... " + (int)Math.floor(100*(linkCount*1.0)/(passes*1.0)) + "%");
    }
    cp5.getController("blurslider").setValue(100*(linkCount*1.0)/(passes*1.0));
    image(blurImg, 10, 39, 350, 350);
    redraw();
  } else {
    logError("Please load an image first!");
  }
}

// set system look and feel 
void setup() {
  PFont font = createFont("arial", 12);
  size(800, 600);
  noStroke();
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
  
  cp5.getTooltip().setDelay(500);
  cp5.getTooltip().register("loadFile","Changes the size of the ellipse.");
  
  running = true;
  
  logConsole("UI Loaded Successfully!");
  
  //scan USB COM ports
  listSerialPorts();
  
  logConsole("READY!");
  logConsole("Please open an image file to begin.");
}

void resampleImage() {
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

        pixelFrame[x][y][0] = (byte)red(tempimage.pixels[pos]);
        pixelFrame[x][y][1] = (byte)green(tempimage.pixels[pos]);
        pixelFrame[x][y][2] = (byte)blue(tempimage.pixels[pos]);
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

void resetResample() {
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

void knobBlurPasses(int theValue) {
  passes = theValue;
}
void knobBlurRadius(int theValue) {
  blurSize = theValue;
  v = 1.0 / ((blurSize * 2.0 + 1) * (blurSize * 2.0 + 1));
}
void knobBlurBloom(int theValue) {
  blurBloom = 1.0 + ((1.0 * theValue/25.0)/20.0);
}

void dsXStart(int value) {
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

void dsXEnd(int value) {
  if ( running ) {
    if (value > 0) resample_x_end = value - 1;
  }
}

void dsYStart(int value) {
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

void dsYEnd(int value) {
  if ( running ) {
    if (value > 0) resample_y_end = value - 1;
  }
}

void openImage() {
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

void draw() {
  background(15, 15, 15);
  fill(0, 0, 0);
  rect(0, 0, 370, 400);
  if (tempimage != null) { 
    image(tempimage, 10, 39, 350, 350);
    noFill();
    stroke(204, 102, 0);
    strokeWeight(2);
    rect(10 + (resample_x_start*1.0)/(tempimage.width*1.0)*350, 39 + (resample_y_start*1.0)/(tempimage.height*1.0)*350, (resample_x_end*1.0 - resample_x_start*1.0)/(tempimage.width*1.0)*350, (resample_y_end*1.0 - resample_y_start*1.0)/(tempimage.height*1.0)*350);
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
