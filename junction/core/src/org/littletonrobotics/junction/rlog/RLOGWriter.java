package org.littletonrobotics.junction.rlog;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Date;

import edu.wpi.first.wpilibj.DriverStation;
import edu.wpi.first.wpilibj.DriverStation.MatchType;

import org.littletonrobotics.junction.LogDataReceiver;
import org.littletonrobotics.junction.LogTable;
import org.littletonrobotics.junction.Logger;

/** Records log values to the RLOG format. */
public class RLOGWriter implements LogDataReceiver {
  private static final double writePeriodSecs = 0.25;
  private static final double timestampUpdateDelay = 3.0; // Wait several seconds to ensure timezone is updated

  private String folder;
  private String filename;

  private boolean autoRename = false;
  private boolean updatedTime = false;
  private boolean updatedMatch = false;
  private Double firstUpdatedTime = null;

  private FileOutputStream fileStream;
  private RLOGEncoder encoder;

  private ByteBuffer writeBuffer = ByteBuffer.allocate(0);
  private double lastWriteTimestamp = 0.0;

  private boolean openFault = false;
  private boolean writeFault = false;

  /**
   * Create a new RLOGWriter for writing to a ".rlog" file.
   * 
   * @param path Path to log file or folder. If only a folder is provided, the
   *             filename will be generated based on the current time and match
   *             number (if applicable).
   */
  public RLOGWriter(String path) {
    if (path.endsWith(".rlog")) {
      File pathFile = new File(path);
      folder = pathFile.getParent() + "/";
      filename = pathFile.getName();
      autoRename = false;
    } else if (path.endsWith("/")) {
      folder = path;
      filename = "temp.rlog";
      autoRename = true;
    } else {
      folder = path + "/";
      filename = "temp.rlog";
      autoRename = true;
    }
  }

  public void start() {
    encoder = new RLOGEncoder();
    lastWriteTimestamp = 0.0;
    try {
      new File(folder + filename).delete();
      fileStream = new FileOutputStream(folder + filename);
    } catch (FileNotFoundException e) {
      openFault = true;
      DriverStation.reportError("Failed to open log file. Data will NOT be recorded.", true);
    }
  }

  public void end() {
    if (fileStream != null) {
      try {
        fileStream.close();
        fileStream = null;
      } catch (IOException e) {
      }
    }
  }

  public void putTable(LogTable table) {
    if (fileStream != null) {
      // Auto rename
      if (autoRename) {

        // Update timestamp
        if (!updatedTime) {
          if (System.currentTimeMillis() > 1638334800000L) { // 12/1/2021, the RIO 2 defaults to 7/1/2021
            if (firstUpdatedTime == null) {
              firstUpdatedTime = Logger.getInstance().getRealTimestamp() / 1000000.0;
            } else if (Logger.getInstance().getRealTimestamp() - firstUpdatedTime > timestampUpdateDelay) {
              rename(new SimpleDateFormat("'Log'_yy-MM-dd_HH-mm-ss'.rlog'").format(new Date()));
              updatedTime = true;
            }
          }

          // Update match
        } else if (DriverStation.getMatchType() != MatchType.None && !updatedMatch) {
          String matchText = "";
          switch (DriverStation.getMatchType()) {
            case Practice:
              matchText = "p";
              break;
            case Qualification:
              matchText = "q";
              break;
            case Elimination:
              matchText = "e";
              break;
            default:
              break;
          }
          matchText += Integer.toString(DriverStation.getMatchNumber());
          rename(filename.substring(0, filename.length() - 5) + "_" + matchText + ".rlog");
          updatedMatch = true;
        }
      }

      // Save to buffer
      encoder.encodeTable(table);
      writeBuffer = ByteBuffer.allocate(writeBuffer.capacity() + encoder.getOutput().capacity())
          .put(writeBuffer.array()).put(encoder.getOutput().array());

      // Write data to file
      if (Logger.getInstance().getRealTimestamp() - lastWriteTimestamp > writePeriodSecs) {
        lastWriteTimestamp = Logger.getInstance().getRealTimestamp();
        try {
          fileStream.write(writeBuffer.array());
          fileStream.getFD().sync();
          writeBuffer = ByteBuffer.allocate(0);
          writeFault = false;
        } catch (IOException e) {
          writeFault = true;
          DriverStation.reportError("Failed to write data to log file.", true);
        }
      }
    }
  }

  private void rename(String newFilename) {
    try {
      fileStream.close();

      File oldFile = new File(folder + filename);
      File newFile = new File(folder + newFilename);
      Files.move(oldFile.toPath(), newFile.toPath());
      filename = newFilename;

      fileStream = new FileOutputStream(folder + filename, true);
    } catch (IOException e) {
      openFault = true;
      DriverStation.reportError("Failed to rename log file. Data will NOT be recorded.", true);
    }
  }

  /**
   * Returns the state of the open file fault. This is tripped when the
   * log file cannot be opened, meaning that data is not being saved.
   */
  public boolean getOpenFault() {
    return openFault;
  }

  /**
   * Returns the state of the write fault. This is tripped when the
   * data cannot be written to the log file.
   */
  public boolean getWriteFault() {
    return writeFault;
  }
}