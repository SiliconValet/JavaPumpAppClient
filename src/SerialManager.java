//
//import java.io.Serializable;
//import java.util.logging.Level;
//import java.util.logging.Logger;
//
//public class SerialManager implements Serializable {
////  static SerialPort serialPort;
//  int c = 0;
//  /**
//   * The constant receivedData.
//   */
//  public static String receivedData;
//  public boolean isConnected = false;
//  private boolean isDebugEnabled = false;
//
//  public SerialManager() {}
//
//  /**
//   * Get available ports string [ ].
//   *
//   * @return the string [ ]
//   */
//  public String[] getAvailablePorts() {
//    String[] portNames = SerialPortList.getPortNames();
//    if (isDebugEnabled) {
//      for (String portName : portNames) {
//        System.out.println(portName);
//      }
//    }
//    return portNames;
//  }
//
//  /**
//   * Connect to Serial port.
//   *
//   * @param portName
//   * @return boolean
//   * TRUE on success, FALSE on failure.
//   */
//  public boolean connectPort(String portName) {
//    serialPort = new SerialPort(portName);
//    try {
//      serialPort.openPort();
//      serialPort.setParams(SerialPort.BAUDRATE_19200,
//        SerialPort.DATABITS_8,
//        SerialPort.STOPBITS_1,
//        SerialPort.PARITY_NONE);
//      isConnected = true;
//      if (isDebugEnabled) {
//        System.out.println(portName + " Connected");
//      }
//      return true;
//    } catch (SerialPortException ex) {
//      System.out.println(ex);
//      isConnected = false;
//    }
//    return false;
//  }
//
//  /**
//   * Read data.
//   */
//  public void readData() {
//    try {
//      serialPort.setFlowControlMode(SerialPort.FLOWCONTROL_RTSCTS_IN |
//        SerialPort.FLOWCONTROL_RTSCTS_OUT);
//      serialPort.addEventListener(new SerialPortReader(), SerialPort.MASK_RXCHAR);
//    } catch (SerialPortException ex) {
//      Logger.getLogger(MainUI.class.getName()).log(Level.SEVERE, null, ex);
//    }
//  }
//
//  /**
//   * Write data.
//   *
//   * @param s the s
//   */
//  public void writeData(String s) {
//    if (isConnected) {
//      try {
//        serialPort.writeBytes(s.getBytes());
//      } catch (SerialPortException ex) {
//        Logger.getLogger(MainUI.class.getName()).log(Level.SEVERE, null, ex);
//      }
//    }
//
//  }
//
//  /**
//   * Disconnect port.
//   */
//  public void disconnectPort() {
//    if (isConnected) {
//      try {
//        serialPort.closePort();
//        isConnected = false;
//        System.out.println(serialPort.getPortName() + " Disconnected");
//      } catch (SerialPortException ex) {
//        Logger.getLogger(MainUI.class.getName()).log(Level.SEVERE, null, ex);
//      }
//    }
//  }
//
//
//  /**
//   * The type Serial port reader.
//   */
//  static class SerialPortReader implements SerialPortEventListener {
//
//    @Override
//    public void serialEvent(SerialPortEvent event) {
//      if (event.isRXCHAR() && event.getEventValue() > 0) {
//        try {
//          SerialManager.receivedData = SerialManager.serialPort.readString(event.getEventValue());
//          System.out.println("Received response: " + SerialManager.receivedData);
//        } catch (SerialPortException ex) {
//          System.out.println("Error in receiving string from COM-port: " + ex);
//        }
//      }
//    }
//
//  }
//
//}