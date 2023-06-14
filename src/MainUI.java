import com.fazecast.jSerialComm.*;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.Plot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYItemRenderer;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.data.time.Millisecond;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.ColorModel;
import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.plaf.FontUIResource;
import javax.swing.text.StyleContext;

import javax.swing.JFileChooser;

import java.io.BufferedReader;


/**
 * The type Main ui.
 */
public class MainUI extends JFrame {
  public static final String version = "1.1.5";
  public static final int logBufferSize = 20000;
  private JSlider PrimeSpeedSlider;
  private JPanel rootPanel;
  private JButton connectDevice;
  private JButton loadDataButton;
  private ChartPanel motorFeedbackPanel;
  private ChartPanel pressureChartPanel;
  private Plot PressureChartPlot;
  private JTextArea logTextArea;

  /* @param PrimeButton Button to prime the pump */
  private JButton PrimeButton;
  private JComboBox<String> DeviceComboBox;
  private JRadioButton connectedRadioButton;
  private JScrollPane logScrollPane;
  private JScrollBar logScrollBar;
  private JButton runButton;
  private JSlider ScaleInput;
  private JTabbedPane tabbedPane1;
  private JTextArea instructionsTextArea;
  private JPanel instructionsTextPanel;
  private JPanel logPanel;
  private JLabel deviceChooserLabel;
  private JPanel ChartPanelContainer;
  private JLabel scaleAmplitudeLabel;
  private JTextField timeStepMS;
  private JButton timeStepMSButton;
  private JCheckBox debugCheckBox;
  private JCheckBox disablePressureDataCheckBox;
  private JSlider movingAvgSlider;
  private JButton shutDownPiButton;

  public volatile SerialPort serialPort;

  private volatile boolean deviceIsConnected = false;
  private boolean motorPriming = false;
  private boolean motorRunning = false;
  private TimeSeries pressureSensorSeries;
  private TimeSeries avgPressureSensorSeries;
  private XYSeries waveformDataSeries;
  private PrintStream deviceOutputStream;
  private BufferedReader deviceInputStream;
  private Socket networkDeviceSocket = null;
  private String deviceConnectedType = "None";
  private double[] movingAvgLastValues = new double[30];
  private int movingAvgIndex = 0;
  private int movingAvgWindowSize = 10;
  private BlockingQueue<String> inputQueue = new LinkedBlockingQueue<>();
  private BlockingQueue<String> outputQueue = new LinkedBlockingQueue<>();
  ArrayList<Float> waveformData = new ArrayList<Float>();

  /**
   * The entry point of application.
   *
   * @param args the input arguments
   */
  public static void main(String[] args) {
    JFrame frame = new MainUI("Pulsatile flow pump UX v" + version);

    while (frame.isActive()) {
      System.out.println("Parallel execution!");

      try {
        TimeUnit.SECONDS.sleep(3);
      } catch (InterruptedException e) {
        System.out.println(e.getMessage());
        throw new RuntimeException(e);
      }
    }
  }

  /**
   * Build our form.
   *
   * @param title the title
   */
  public MainUI(String title) {
    super(title);

    // Call generated code.
    $$$setupUI$$$();

    // Set the default content pane to our container from GUI designer.
    this.setContentPane(this.rootPanel);
    this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

    // Populate the serial port combo box on launch.
    this.populateSerialPortsComboBox();

    this.pack();
    this.setVisible(true);

    // Connect to serial port on button click.
    addListenerForConnectDeviceButton();
    addListenerForLoadDataButton();
    addListenerForPrimeButton();
    addListenerForPrimeSpeedSlider();
    addListenerForRunButton();
    addListenerForScaleSlider();
    addListenerForTimeStepMSButton();
    addListenerForDebugCheckbox();
    addListenerForWindowClose();
    addListenerForDisablePressureDataCheckbox();
    addListenerForMovingAvgSlider();
    addListenerForShutDownPiButton();
  }

  private void addListenerForShutDownPiButton() {
    shutDownPiButton.addActionListener(new ActionListener() {
      /**
       * @param e the event to be processed
       */
      @Override
      public void actionPerformed(ActionEvent e) {
        if (!deviceIsConnected) {
          logger("Device not connected");
          return;
        }
        String cmd;
        cmd = "!:SD\n";
        sendDeviceData(cmd.getBytes());
        logger("Shutdown command '" + cmd.replaceAll("[\\r\\n]+", "") + "' sent");

      }
    });
  }

  private void addListenerForMovingAvgSlider() {
    movingAvgSlider.addChangeListener(new ChangeListener() {
      /**
       * @param e a ChangeEvent object
       */
      @Override
      public void stateChanged(ChangeEvent e) {
        movingAvgWindowSize = movingAvgSlider.getValue();
      }
    });
  }

  private void addListenerForDisablePressureDataCheckbox() {
    disablePressureDataCheckBox.addActionListener(new ActionListener() {
      /**
       * @param e the event to be processed
       */
      @Override
      public void actionPerformed(ActionEvent e) {
        if (!deviceIsConnected) {
          logger("Device not connected");
          return;
        }
        String cmd;
        cmd = "Z:" + (disablePressureDataCheckBox.isSelected() ? 'T' : 'F') + "\n";
        sendDeviceData(cmd.getBytes());
        logger("Pressure data command '" + cmd.replaceAll("[\\r\\n]+", "") + "' sent");

      }
    });
  }

  private void addListenerForWindowClose() {
    addWindowListener(new WindowAdapter() {
      @Override
      public void windowClosing(WindowEvent e) {
        try {
          logger("Closing sockets");
          networkDeviceSocket.close();
        } catch (IOException ex) {
          logger("Exception on attempt to close socket " + ex.getMessage());
          throw new RuntimeException(ex);
        }
      }
    });
  }

  private void addListenerForDebugCheckbox() {
    debugCheckBox.addActionListener(new ActionListener() {
      /**
       * @param e the event to be processed
       */
      @Override
      public void actionPerformed(ActionEvent e) {
        if (!deviceIsConnected) {
          logger("Serial port not connected");
          return;
        }
        String cmd;
        cmd = "D:" + (debugCheckBox.isSelected() ? 'T' : 'F') + "\n";
        sendDeviceData(cmd.getBytes());
        logger("Debug command '" + cmd.replaceAll("[\\r\\n]+", "") + "' sent");
      }
    });
  }

  private void addListenerForTimeStepMSButton() {
    timeStepMSButton.addActionListener(new ActionListener() {
      /**
       * @param e the event to be processed
       */
      @Override
      public void actionPerformed(ActionEvent e) {
        if (!deviceIsConnected) {
          logger("Device not connected");
          return;
        }

        if (e.getActionCommand().equals("Update")) {
          String cmd;
          cmd = "F:" + timeStepMS.getText() + "\n";
          sendDeviceData(cmd.getBytes());
          logger("Time step command '" + cmd.replaceAll("[\\r\\n]+", "") + "' sent");
        }

      }
    });
  }

  private void addListenerForScaleSlider() {
    ScaleInput.addChangeListener(new ChangeListener() {
      /**
       * @param e a ChangeEvent object
       */
      @Override
      public void stateChanged(ChangeEvent e) {
        JSlider source = (JSlider) e.getSource();
        if (!source.getValueIsAdjusting()) {
          if (deviceIsConnected) {
            String cmd;
            cmd = "X:" + ScaleInput.getValue() + "\n";
            sendDeviceData(cmd.getBytes());
            logger("Scale command '" + cmd.replaceAll("[\\r\\n]+", "") + "' sent");
          } else {
            logger("Serial port not connected");
          }
        }
      }
    });
  }

  private void addListenerForRunButton() {
    runButton.addActionListener(new ActionListener() {
      /**
       * @param e the event to be processed
       */
      @Override
      public void actionPerformed(ActionEvent e) {
        if (!deviceIsConnected) {
          logger("Device not connected");
          return;
        }

        if (e.getActionCommand().equals("Run")) {
          runButton.setText("Stop");
          runButton.setBackground(Color.RED);
          motorRunning = true;
          sendDeviceData("R:un\n".getBytes());
          logger("Run command 'R:un' sent");
        } else {
          runButton.setText("Run");
          runButton.setBackground(Color.GREEN);
          sendDeviceData("S:top\n".getBytes());
          logger("Run command 'S:top' sent");
          motorRunning = false;
        }

      }
    });
  }

  private void addListenerForPrimeSpeedSlider() {
    PrimeSpeedSlider.addChangeListener(new ChangeListener() {
      /**
       * @param e a ChangeEvent object
       */
      @Override
      public void stateChanged(ChangeEvent e) {
        JSlider source = (JSlider) e.getSource();
        if (!source.getValueIsAdjusting()) {
          if (motorPriming) {
            String cmd;
            if (deviceIsConnected) {
              cmd = "P:" + PrimeSpeedSlider.getValue() + "\n";
              sendDeviceData(cmd.getBytes());
              logger("Prime command '" + cmd.replaceAll("[\\r\\n]+", "") + "' sent");
            } else {
              logger("Serial port not connected");
            }
          }
        }
      }
    });
  }

  private void addListenerForPrimeButton() {
    PrimeButton.addActionListener(new ActionListener() {
      /**
       * @param e the event to be processed
       */
      @Override
      public void actionPerformed(ActionEvent e) {
        if (e.getActionCommand().equals("Prime")) {

          motorPriming = true;
          PrimeButton.setText("Stop");
          if (deviceIsConnected) {
            String cmd;
            cmd = "P:" + PrimeSpeedSlider.getValue() + "\n";
            sendDeviceData(cmd.getBytes());
          } else {
            logger("Device not connected");
          }
        } else {
          motorPriming = false;
          sendDeviceData("S:top\n".getBytes());
          PrimeButton.setText("Prime");
          if (deviceIsConnected) {
            String cmd;
            cmd = "H:ome\n";
            sendDeviceData(cmd.getBytes());
          } else {
            logger("Device not connected");
          }
        }
      }
    });
  }

  /**
   * Add listener for load data button.
   */
  private void addListenerForLoadDataButton() {
    loadDataButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        JFileChooser fileChooser = new JFileChooser();
        // Filter the allowable file types.
        fileChooser.setFileFilter(new FileNameExtensionFilter(
          "Delimited files", "csv", "tsv"
        ));
        fileChooser.setCurrentDirectory(new File(System.getProperty("user.home")));
        int result = fileChooser.showOpenDialog(rootPanel);

        if (result == JFileChooser.APPROVE_OPTION) {
          File selectedFile = fileChooser.getSelectedFile();
          // (Re)initialize the waveform data array.
          MainUI.this.waveformData = new ArrayList<Float>();
          loadSelectedFile(selectedFile, MainUI.this.waveformData);
          logger("Loaded waveform '" + selectedFile.getAbsolutePath() + "'");
          logger("Waveform length: " + MainUI.this.waveformData.size());

          sendDeviceData(("L:" + MainUI.this.waveformData.size() + "\n").getBytes());

          for (Float waveformDatum : MainUI.this.waveformData) {
            sendDeviceData((waveformDatum + "\n").getBytes());
          }
          sendDeviceData("\n".getBytes());

          SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
              waveformDataSeries.clear();
              for (int i = 0; i < MainUI.this.waveformData.size(); i++) {
                waveformDataSeries.addOrUpdate(
                  (double) i,
                  (double) MainUI.this.waveformData.get(i)
                );
              }
            }
          });
        }

      }
    });
  }

  /**
   * Respond to the button click for connect button.
   */
  private void addListenerForConnectDeviceButton() {
    connectDevice.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        deviceConnect(e);
      }
    });
  }

  private void loadSelectedFile(File selectedFile, ArrayList<Float> waveformData) {
    System.out.println("Selected file: " + selectedFile.getAbsolutePath());
    BufferedReader bf;
    try {
      logger("Loading waveform '" + selectedFile.getAbsolutePath() + "'");

      bf = new BufferedReader(new FileReader(selectedFile));
      String line = bf.readLine();
      // checking for end of file.
      while (line != null) {
        waveformData.add(Float.parseFloat(line));
        line = bf.readLine();
      }

      // Close file.
      bf.close();
    } catch (IOException ex) {
      JDialog d = new JDialog();
      d.add(new JLabel("File could not be opened" + ex));
      d.setVisible(true);
    }
  }

  private void sendSerialData(byte[] cmd) {
    if (deviceIsConnected) {
      try {
        deviceOutputStream.write(cmd);
        logger("[Serial] Sent '" + (new String(cmd, StandardCharsets.UTF_8)).trim());
      } catch (IOException ex) {
        logger("[Serial] Error sending '" + (new String(cmd, StandardCharsets.UTF_8)).trim());
      }
    } else {
      logger("Serial port not connected");
    }
  }

  private void sendNetworkData(byte[] cmd) {
    if (deviceIsConnected) {
      outputQueue.add(new String(cmd).trim());
      //logger("[Network] queued '" + (new String(cmd, StandardCharsets.UTF_8)).trim() + "'");
    } else {
      logger("Network device not connected");
    }
  }

  private synchronized void sendDeviceData(byte[] cmd) {
    if (deviceIsConnected) {
      switch (deviceConnectedType) {
        case "Serial" -> sendSerialData(cmd);
        case "Network" -> sendNetworkData(cmd);
        default -> logger("Error: No device connected");
      }
    }
  }

  private void deviceConnect(ActionEvent e) {
    if (DeviceComboBox.getSelectedItem() != null) {
      String actionCommand = e.getActionCommand();
      String selectedDevice = DeviceComboBox.getSelectedItem().toString();
      // Connect to the device.
      if (selectedDevice.equals("Network device")) {
        deviceConnectedType = "Network";
        manageNetworkDeviceConnection(actionCommand);
      } else {
        deviceConnectedType = "Serial";
        manageSerialDeviceConnection(actionCommand);
      }

      if (deviceIsConnected) {
        connectedRadioButton.setEnabled(true);
        loadDataButton.setEnabled(true);
        connectDevice.setText("Disconnect");
        shutDownPiButton.setEnabled(true);
        disablePressureDataCheckBox.setEnabled(true);

        logger("Connected to " + deviceConnectedType + " device");

        // Set all defaults.
        sendDeviceData(("D:" + (debugCheckBox.isSelected() ? 'T' : 'F')).getBytes());
        sendDeviceData("S:top".getBytes());
        // set starter step frequency
        sendDeviceData(("F:" + timeStepMS.getText()).getBytes());
        sendDeviceData(("Z:" + (disablePressureDataCheckBox.isSelected() ? 'T' : 'F')).getBytes());

      } else {
        setDeviceDisconnected();
      }
    }
  }

  private void setDeviceDisconnected() {
    deviceIsConnected = false;
    deviceConnectedType = "None";
    connectedRadioButton.setEnabled(false);
    runButton.setText("Run");
    runButton.setEnabled(false);
    loadDataButton.setEnabled(false);
    disablePressureDataCheckBox.setEnabled(false);
    connectDevice.setText("Connect");
    shutDownPiButton.setEnabled(false);
  }

  private void manageNetworkDeviceConnection(String actionCommand) {
    switch (actionCommand) {
      case "Connect" -> {
        try {
          InetAddress address = InetAddress.getByName("pumpapp.local");
          logger("Connecting to: " + address.getHostAddress());
          networkDeviceSocket = new Socket("pumpapp.local", 9999);
          // Connect to the device over the network.
          deviceIsConnected = true;

          // Create input and output streams to read from and write to the server
          deviceOutputStream = new PrintStream(networkDeviceSocket.getOutputStream());
          deviceInputStream = new BufferedReader(new InputStreamReader(networkDeviceSocket.getInputStream()));

          Thread inputMonitor = createStreamInputMonitorThread();
          Thread outputMonitor = createStreamOutputMonitorThread();

          inputMonitor.start();
          outputMonitor.start();

        } catch (IOException ex) {
          deviceIsConnected = false;
          logger("Error connecting to network device: " + ex);
        }
      }
      case "Disconnect" -> {
        deviceIsConnected = false;
        deviceOutputStream.close();
        try {
          deviceInputStream.close();
        } catch (IOException ex) {
          throw new RuntimeException(ex);
        }
      }
    }
  }

  private void manageSerialDeviceConnection(String actionCommand) {
    switch (actionCommand) {
      case "Connect" -> {
        String port = (String) DeviceComboBox.getSelectedItem();
        logger("Connecting to: " + port);
        SerialPort[] portList = SerialPort.getCommPorts();
        serialPort = portList[DeviceComboBox.getSelectedIndex()];
        serialPort.setBaudRate(500000);
        serialPort.flushIOBuffers();
        serialPort.openPort();
        deviceIsConnected = serialPort.isOpen();
        addDeviceReadListener(serialPort);
      }
      case "Disconnect" -> {
        deviceIsConnected = false;
        serialPort.closePort();
      }
    }
  }

  /**
   * Monitors the input stream from the device.
   *
   * @return Thread to monitor the input stream.
   */
  private Thread createStreamInputMonitorThread() {
    return new Thread(new Runnable() {
      @Override
      public void run() {
        while (deviceIsConnected) {
          try {
            String line = deviceInputStream.readLine();
            if (line != null) {
              if (debugCheckBox.isSelected()) {
                logger("[Data] Received '" + line + "'");
              }
              parseIncomingLine(line);
            }
          } catch (SocketException ex) {
            logger("Disconnected!");
            setDeviceDisconnected();
          } catch (IOException ex) {
            throw new RuntimeException(ex);
          }
        }
      }
    });
  }

  private Thread createStreamOutputMonitorThread() {
    return new Thread(new Runnable() {
      @Override
      public void run() {
        while (deviceIsConnected) {
          try {
            String line = outputQueue.take().trim();
            if (debugCheckBox.isSelected()) {
              logger("[OutData] sent '" + line + "'");
            }
            deviceOutputStream.write((line + "\n").getBytes());
          } catch (InterruptedException ex) {
            logger("Exception thrown in queue manager for data: " + ex.getMessage());
            throw new RuntimeException(ex);
          } catch (IOException e) {
            logger("Could not write to output stream: " + e.getMessage());
            throw new RuntimeException(e);
          }
        }
      }
    });
  }

  /**
   * Add a listener to the serial port to read incoming data.
   * This is only run if using serial port logic.
   *
   * @param activePort Active serial port.
   */
  private void addDeviceReadListener(SerialPort activePort) {
//    activePort.addDataListener(new SerialPortDataListener() {
    activePort.addDataListener(new SerialPortMessageListener() {

      @Override
      public int getListeningEvents() {
        return SerialPort.LISTENING_EVENT_DATA_RECEIVED;
      }

      @Override
      public byte[] getMessageDelimiter() {
        return new byte[]{(byte) 0x0D, (byte) 0x0A};
      }

      @Override
      public boolean delimiterIndicatesEndOfMessage() {
        return true;
      }

      @Override
      public void serialEvent(SerialPortEvent serialPortEvent) {
        String incomingLine;
        incomingLine = new String(serialPortEvent.getReceivedData()).trim();
        parseIncomingLine(incomingLine);
      }
    });
  }

  /**
   * Parse the incoming line from the device.
   *
   * @param line String Incoming line.
   */
  private void parseIncomingLine(String line) {
    String[] values = line.trim().split(":");
    switch (values[0]) {
      case "E" -> logger("Error: " + values[1]);
      case "A" -> logger("Ack set motor speed: " + values[1]);
      case "D" -> {
        logger("Data loaded to microcontroller: " + values[1] + " lines");
        runButton.setEnabled(true);
      }
      case "V" -> logger("Info: Device connected, Software Version " + values[1]);
      case "I" -> logger("Info: " + values[1]);
      case "P" -> {
        double pval = Double.parseDouble(values[1]);
        SwingUtilities.invokeLater(new Runnable() {
          @Override
          public void run() {
            pressureSensorSeries.addOrUpdate(
              new Millisecond(),
              pval - 730.0
            );

            avgPressureSensorSeries.addOrUpdate(
              new Millisecond(),
              calculateMovingAverage(pval - 730.0)
            );
          }
        });
      }
      default -> System.out.println("Unexpected command: " + values[0] + " with value " + values[1]);
    }
  }

  /**
   * Calculate the moving average of the last x values.
   * <p>
   * X is defined by the movingAvgWindowSize property.
   *
   * @param latestValue Latest value to add to the moving average.
   * @return Moving average of the last x values.
   */
  private float calculateMovingAverage(double latestValue) {
    movingAvgLastValues[movingAvgIndex] = latestValue;
    movingAvgIndex++;
    if (movingAvgIndex >= movingAvgWindowSize) {
      movingAvgIndex = 0;
    }
    float movingAvgSum = 0;
    for (int i = 0; i < movingAvgWindowSize; i++) {
      movingAvgSum += movingAvgLastValues[i];
    }
    return movingAvgSum / movingAvgWindowSize;
  }

  /**
   * Log text to the log text area.
   *
   * @param text Text to be logged.
   */
  private void logger(String text) {
    System.out.println(text);
    SwingUtilities.invokeLater(new Runnable() {
      @Override
      public void run() {
        logTextArea.append(text + "\n");
        logTextArea.setCaretPosition(logTextArea.getDocument().getLength());

        // Cycle the logs.
        if (logTextArea.getDocument().getLength() > logBufferSize) {
          logTextArea.setText("");
        }
      }
    });
  }

  /**
   * Populate the combo box for serial ports.
   */
  private void populateSerialPortsComboBox() {
    this.DeviceComboBox.removeAllItems();

    this.DeviceComboBox.addItem("Network device");

    SerialPort[] portList = SerialPort.getCommPorts();
    for (SerialPort port : portList) {
      this.DeviceComboBox.addItem(port.getSystemPortName());
    }

    this.DeviceComboBox.setEnabled(true);
  }

  /**
   * Called by form to instantiate the non-swing native components.
   */
  private void createUIComponents() {
    DefaultCategoryDataset dataset1, dataset2;

    dataset1 = new DefaultCategoryDataset();
    dataset1.setValue(0, "Pressure sensor", "0");

    dataset2 = new DefaultCategoryDataset();
    dataset2.setValue(0, "Motor velocity", "0");

    pressureSensorSeries = new TimeSeries("Pressure sensor");
    pressureSensorSeries.setMaximumItemCount(300);
    TimeSeriesCollection pressureChartTimeSeriesCollection = new TimeSeriesCollection(pressureSensorSeries);


    avgPressureSensorSeries = new TimeSeries("Smoothed");
    avgPressureSensorSeries.setMaximumItemCount(300);
    pressureChartTimeSeriesCollection.addSeries(avgPressureSensorSeries);

    waveformDataSeries = new XYSeries("Motor feedback");
    waveformDataSeries.setMaximumItemCount(110);
    XYSeriesCollection waveformDataChartTimeSeriesCollection = new XYSeriesCollection(waveformDataSeries);

    JFreeChart pressureChart = this.createPressureChart(pressureChartTimeSeriesCollection);
    JFreeChart motorFeedbackChart = this.createMotorFeedbackChart(waveformDataChartTimeSeriesCollection);

    this.pressureChartPanel = new ChartPanel(pressureChart);
    this.motorFeedbackPanel = new ChartPanel(motorFeedbackChart);


  }

  /**
   * Create the JFreeChart pressure chart from params.
   *
   * @param dataset Data set to use for the chart.
   * @return JFreeChart object.
   */
  private JFreeChart createPressureChart(TimeSeriesCollection dataset) {
    JFreeChart chart = ChartFactory.createTimeSeriesChart(
      "Pressure",
      "Time",
      "Sensor value",
      dataset,
      true,
      true,
      false
    );

    // Set the range as auto-ranging.
    XYPlot plot = (XYPlot) chart.getPlot();
    //plot.getRangeAxis().setRange(730.0, 820.0);
    plot.getRangeAxis().setAutoRange(true);
    plot.getRangeAxis().setLowerMargin(0.1);
    plot.getRangeAxis().setUpperMargin(0.1);

    XYItemRenderer r = plot.getRenderer(0);
    r.setSeriesPaint(0, new Color(0xB9B9B9));
    r.setSeriesPaint(1, Color.BLUE);

    return chart;
  }

  /**
   * Create the JFreeChart motor feedback chart from params.
   *
   * @param dataset Data set to use for the chart.
   * @return JFreeChart object.
   */
  private JFreeChart createMotorFeedbackChart(XYSeriesCollection dataset) {
    JFreeChart chart = ChartFactory.createXYLineChart(
      "Motor",
      "Time",
      "Position value",
      dataset,
      PlotOrientation.VERTICAL,
      true,
      true,
      false
    );

    // Set the domain as auto-ranging.
    XYPlot plot = (XYPlot) chart.getPlot();
    plot.getRangeAxis().setAutoRange(true);

    return chart;
  }


  /**
   * Method generated by IntelliJ IDEA GUI Designer
   * >>> IMPORTANT!! <<<
   * DO NOT edit this method OR call it in your code!
   *
   * @noinspection ALL
   */
  private void $$$setupUI$$$() {
    createUIComponents();
    rootPanel = new JPanel();
    rootPanel.setLayout(new GridBagLayout());
    rootPanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLoweredBevelBorder(), null, TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, null, null));
    PrimeSpeedSlider = new JSlider();
    PrimeSpeedSlider.setInverted(false);
    PrimeSpeedSlider.setMajorTickSpacing(10);
    PrimeSpeedSlider.setMaximum(100);
    PrimeSpeedSlider.setMinimum(-100);
    PrimeSpeedSlider.setMinorTickSpacing(5);
    PrimeSpeedSlider.setPaintLabels(true);
    PrimeSpeedSlider.setPaintTicks(true);
    PrimeSpeedSlider.setPaintTrack(true);
    PrimeSpeedSlider.setSnapToTicks(true);
    PrimeSpeedSlider.setValue(0);
    PrimeSpeedSlider.setValueIsAdjusting(false);
    PrimeSpeedSlider.putClientProperty("Slider.paintThumbArrowShape", Boolean.FALSE);
    GridBagConstraints gbc;
    gbc = new GridBagConstraints();
    gbc.gridx = 1;
    gbc.gridy = 3;
    gbc.gridwidth = 2;
    gbc.anchor = GridBagConstraints.WEST;
    gbc.fill = GridBagConstraints.BOTH;
    rootPanel.add(PrimeSpeedSlider, gbc);
    PrimeButton = new JButton();
    PrimeButton.setText("Prime");
    gbc = new GridBagConstraints();
    gbc.gridx = 0;
    gbc.gridy = 3;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    rootPanel.add(PrimeButton, gbc);
    logScrollBar = new JScrollBar();
    gbc = new GridBagConstraints();
    gbc.gridx = 2;
    gbc.gridy = 1;
    gbc.fill = GridBagConstraints.VERTICAL;
    rootPanel.add(logScrollBar, gbc);
    ScaleInput = new JSlider();
    ScaleInput.setMajorTickSpacing(1);
    ScaleInput.setMaximum(10);
    ScaleInput.setMinimum(1);
    ScaleInput.setPaintLabels(true);
    ScaleInput.setPaintTicks(true);
    ScaleInput.setPaintTrack(true);
    ScaleInput.setSnapToTicks(true);
    ScaleInput.setValue(1);
    ScaleInput.setValueIsAdjusting(false);
    ScaleInput.putClientProperty("Slider.paintThumbArrowShape", Boolean.FALSE);
    gbc = new GridBagConstraints();
    gbc.gridx = 1;
    gbc.gridy = 2;
    gbc.gridwidth = 2;
    gbc.anchor = GridBagConstraints.WEST;
    gbc.fill = GridBagConstraints.BOTH;
    rootPanel.add(ScaleInput, gbc);
    scaleAmplitudeLabel = new JLabel();
    scaleAmplitudeLabel.setText("Scale\nAmplitude");
    gbc = new GridBagConstraints();
    gbc.gridx = 0;
    gbc.gridy = 2;
    gbc.anchor = GridBagConstraints.WEST;
    rootPanel.add(scaleAmplitudeLabel, gbc);
    tabbedPane1 = new JTabbedPane();
    gbc = new GridBagConstraints();
    gbc.gridx = 0;
    gbc.gridy = 4;
    gbc.gridwidth = 3;
    gbc.fill = GridBagConstraints.BOTH;
    rootPanel.add(tabbedPane1, gbc);
    logPanel = new JPanel();
    logPanel.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1, false, true));
    tabbedPane1.addTab("Log", logPanel);
    logScrollPane = new JScrollPane();
    logScrollPane.setMaximumSize(new Dimension(32767, 200));
    logScrollPane.setMinimumSize(new Dimension(18, 200));
    logScrollPane.setName("Log");
    logScrollPane.setRequestFocusEnabled(true);
    logScrollPane.setVerticalScrollBarPolicy(22);
    logPanel.add(logScrollPane, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
    logScrollPane.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLoweredBevelBorder(), null, TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, null, null));
    logTextArea = new JTextArea();
    logTextArea.setColumns(30);
    logTextArea.setEditable(false);
    Font logTextAreaFont = this.$$$getFont$$$("Andale Mono", Font.PLAIN, -1, logTextArea.getFont());
    if (logTextAreaFont != null) logTextArea.setFont(logTextAreaFont);
    logTextArea.setMaximumSize(new Dimension(2147483647, 200));
    logTextArea.setMinimumSize(new Dimension(121, 200));
    logTextArea.setRows(5);
    logTextArea.setText("Application Log");
    logTextArea.setToolTipText("Application log");
    logTextArea.setWrapStyleWord(true);
    logScrollPane.setViewportView(logTextArea);
    instructionsTextPanel = new JPanel();
    instructionsTextPanel.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
    tabbedPane1.addTab("Instructions", instructionsTextPanel);
    instructionsTextArea = new JTextArea();
    instructionsTextArea.setEditable(false);
    instructionsTextArea.setEnabled(true);
    instructionsTextArea.setLineWrap(true);
    instructionsTextArea.setRows(15);
    instructionsTextArea.setText("Ensure that the USB is plugged in before starting the application. \nThe device list is only generated on startup. \n\n1. Choose the correct device from the dropdown.\n2. Click the 'Connect' button.\n3. Check the logs for an 'A:0' connection ack\nIf one is not present, the device did not connect properly, \nor you have connected to the wrong device.\n4. Prime the device if appropriate. Note that when you turn \noff priming, the current location is set as 'home' for relative\n positioning later.\n5. Set the Scale factor, this is a multiple to apply to rows in\n the data loaded. If you change this, you must re-load the data.\n6. Click the 'Load data' button and choose a file, note that we \ncurrently only support a single entry per line of a float or \ninteger value. Each row represents an absolute position relative\nto the starting position or last 'Prime'. \nUnits are measured in 1/8 degree by default. (see scaling)\n6. Click the run button.");
    instructionsTextArea.setWrapStyleWord(false);
    instructionsTextPanel.add(instructionsTextArea, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, 50), null, 0, false));
    ChartPanelContainer = new JPanel();
    ChartPanelContainer.setLayout(new GridBagLayout());
    gbc = new GridBagConstraints();
    gbc.gridx = 1;
    gbc.gridy = 0;
    gbc.gridwidth = 2;
    gbc.weightx = 0.8;
    gbc.weighty = 0.6;
    gbc.fill = GridBagConstraints.BOTH;
    rootPanel.add(ChartPanelContainer, gbc);
    ChartPanelContainer.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), null, TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, null, null));
    gbc = new GridBagConstraints();
    gbc.gridx = 1;
    gbc.gridy = 0;
    gbc.weightx = 0.5;
    gbc.weighty = 1.0;
    gbc.fill = GridBagConstraints.BOTH;
    ChartPanelContainer.add(motorFeedbackPanel, gbc);
    pressureChartPanel.setEnabled(true);
    pressureChartPanel.setHorizontalAxisTrace(false);
    pressureChartPanel.setRangeZoomable(false);
    pressureChartPanel.setVerticalAxisTrace(true);
    gbc = new GridBagConstraints();
    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.weightx = 0.5;
    gbc.weighty = 1.0;
    gbc.fill = GridBagConstraints.BOTH;
    ChartPanelContainer.add(pressureChartPanel, gbc);
    final JPanel panel1 = new JPanel();
    panel1.setLayout(new GridLayoutManager(11, 2, new Insets(0, 0, 0, 0), -1, -1));
    gbc = new GridBagConstraints();
    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.fill = GridBagConstraints.BOTH;
    rootPanel.add(panel1, gbc);
    deviceChooserLabel = new JLabel();
    deviceChooserLabel.setText("Choose device");
    panel1.add(deviceChooserLabel, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(152, 17), null, 0, false));
    connectDevice = new JButton();
    connectDevice.setText("Connect");
    panel1.add(connectDevice, new GridConstraints(1, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 1, false));
    debugCheckBox = new JCheckBox();
    debugCheckBox.setText("Debugging enabled");
    panel1.add(debugCheckBox, new GridConstraints(3, 0, 1, 2, GridConstraints.ANCHOR_NORTH, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    connectedRadioButton = new JRadioButton();
    connectedRadioButton.setEnabled(false);
    connectedRadioButton.setForeground(new Color(-1));
    connectedRadioButton.setHideActionText(false);
    connectedRadioButton.setSelected(true);
    connectedRadioButton.setText("Connected");
    connectedRadioButton.putClientProperty("html.disable", Boolean.TRUE);
    panel1.add(connectedRadioButton, new GridConstraints(2, 0, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    runButton = new JButton();
    runButton.setBackground(new Color(-12794841));
    runButton.setEnabled(false);
    runButton.setText("Run");
    panel1.add(runButton, new GridConstraints(6, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    loadDataButton = new JButton();
    loadDataButton.setEnabled(false);
    loadDataButton.setLabel("Load data");
    loadDataButton.setText("Load data");
    panel1.add(loadDataButton, new GridConstraints(6, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(152, 30), null, 0, false));
    timeStepMS = new JTextField();
    timeStepMS.setColumns(6);
    timeStepMS.setText("10");
    timeStepMS.setToolTipText("Number of ms between movement updates");
    panel1.add(timeStepMS, new GridConstraints(8, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(152, 30), null, 0, false));
    timeStepMSButton = new JButton();
    timeStepMSButton.setText("Update");
    panel1.add(timeStepMSButton, new GridConstraints(8, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    final JLabel label1 = new JLabel();
    label1.setText("ms between updates");
    panel1.add(label1, new GridConstraints(7, 0, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    DeviceComboBox = new JComboBox();
    DeviceComboBox.setEnabled(true);
    final DefaultComboBoxModel defaultComboBoxModel1 = new DefaultComboBoxModel();
    defaultComboBoxModel1.addElement("not found");
    DeviceComboBox.setModel(defaultComboBoxModel1);
    DeviceComboBox.setToolTipText("Choose device");
    panel1.add(DeviceComboBox, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    final Spacer spacer1 = new Spacer();
    panel1.add(spacer1, new GridConstraints(10, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1, GridConstraints.SIZEPOLICY_WANT_GROW, null, new Dimension(152, 14), null, 0, false));
    disablePressureDataCheckBox = new JCheckBox();
    disablePressureDataCheckBox.setEnabled(false);
    disablePressureDataCheckBox.setText("Disable pressure data");
    panel1.add(disablePressureDataCheckBox, new GridConstraints(4, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    movingAvgSlider = new JSlider();
    movingAvgSlider.setMajorTickSpacing(1);
    movingAvgSlider.setMaximum(30);
    movingAvgSlider.setMinimum(3);
    movingAvgSlider.setPaintLabels(false);
    movingAvgSlider.setPaintTicks(true);
    movingAvgSlider.setSnapToTicks(false);
    movingAvgSlider.setValue(3);
    panel1.add(movingAvgSlider, new GridConstraints(5, 0, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 3, false));
    shutDownPiButton = new JButton();
    shutDownPiButton.setBackground(new Color(-1767424));
    shutDownPiButton.setEnabled(false);
    shutDownPiButton.setText("Shut down Pi");
    panel1.add(shutDownPiButton, new GridConstraints(9, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    logScrollPane.setVerticalScrollBar(logScrollBar);
    deviceChooserLabel.setLabelFor(DeviceComboBox);
    label1.setLabelFor(timeStepMS);
  }

  /**
   * @noinspection ALL
   */
  private Font $$$getFont$$$(String fontName, int style, int size, Font currentFont) {
    if (currentFont == null) return null;
    String resultName;
    if (fontName == null) {
      resultName = currentFont.getName();
    } else {
      Font testFont = new Font(fontName, Font.PLAIN, 10);
      if (testFont.canDisplay('a') && testFont.canDisplay('1')) {
        resultName = fontName;
      } else {
        resultName = currentFont.getName();
      }
    }
    Font font = new Font(resultName, style >= 0 ? style : currentFont.getStyle(), size >= 0 ? size : currentFont.getSize());
    boolean isMac = System.getProperty("os.name", "").toLowerCase(Locale.ENGLISH).startsWith("mac");
    Font fontWithFallback = isMac ? new Font(font.getFamily(), font.getStyle(), font.getSize()) : new StyleContext().getFont(font.getFamily(), font.getStyle(), font.getSize());
    return fontWithFallback instanceof FontUIResource ? fontWithFallback : new FontUIResource(fontWithFallback);
  }

  /**
   * @noinspection ALL
   */
  public JComponent $$$getRootComponent$$$() {
    return rootPanel;
  }


}
