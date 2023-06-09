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
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.data.time.Millisecond;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Locale;
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
  public static final String version = "1.0.6";
  public static final int logBufferSize = 20000;
  private JSlider PrimeSpeedSlider;
  private JPanel rootPanel;
  private JButton connectSerial;
  private JButton loadDataButton;
  private ChartPanel motorFeedbackPanel;
  private ChartPanel pressureChartPanel;
  private Plot PressureChartPlot;
  private JTextArea logTextArea;

  /* @param PrimeButton Button to prime the pump */
  private JButton PrimeButton;
  private JComboBox<String> SerialPortsComboBox;
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

  public volatile SerialPort serialPort;

  private volatile boolean serialIsConnected = false;
  private boolean motorPriming = false;
  private boolean motorRunning = false;
  private TimeSeries pressureSensorSeries;
  private XYSeries waveformDataSeries;
  private OutputStream serialOutputStream;
  private BufferedReader serialInputStream;
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
    addListenerForConnectSerialButton();
    addListenerForLoadDataButton();
    addListenerForPrimeButton();
    addListenerForPrimeSpeedSlider();
    addListenerForRunButton();
    addListenerForScaleSlider();
    addListenerForTimeStepMSButton();
    addListenerForDebugCheckbox();
  }

  private void addListenerForDebugCheckbox() {
    debugCheckBox.addActionListener(new ActionListener() {
      /**
       * @param e the event to be processed
       */
      @Override
      public void actionPerformed(ActionEvent e) {
        if (!serialIsConnected) {
          logger("Serial port not connected");
          return;
        }
        String cmd;
        cmd = "D:" + (debugCheckBox.isSelected() ? 'F' : 'T') + "\n";
        sendSerialData(cmd.getBytes());
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
        if (!serialIsConnected) {
          logger("Serial port not connected");
          return;
        }

        if (e.getActionCommand().equals("Set")) {
          String cmd;
          cmd = "F:" + timeStepMS.getText() + "\n";
          sendSerialData(cmd.getBytes());
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
          if (serialIsConnected) {
            String cmd;
            cmd = "X:" + ScaleInput.getValue() + "\n";
            sendSerialData(cmd.getBytes());
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
        if (!serialIsConnected) {
          logger("Serial port not connected");
          return;
        }

        if (e.getActionCommand().equals("Run")) {
          runButton.setText("Stop");
          runButton.setBackground(Color.RED);
          motorRunning = true;
          sendSerialData("R:un\n".getBytes());
          logger("Run command 'R:un' sent");
        } else {
          runButton.setText("Run");
          runButton.setBackground(Color.GREEN);
          sendSerialData("S:top\n".getBytes());
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
            if (serialIsConnected) {
              cmd = "P:" + PrimeSpeedSlider.getValue() + "\n";
              sendSerialData(cmd.getBytes());
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
          if (serialIsConnected) {
            String cmd;
            cmd = "P:" + PrimeSpeedSlider.getValue() + "\n";
            sendSerialData(cmd.getBytes());
          } else {
            logger("Serial port not connected");
          }
        } else {
          motorPriming = false;
          sendSerialData("S:top\n".getBytes());
          PrimeButton.setText("Prime");
          if (serialIsConnected) {
            String cmd;
            cmd = "H:ome\n";
            sendSerialData(cmd.getBytes());
          } else {
            logger("Serial port not connected");
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

          sendSerialData(("L:" + String.valueOf(MainUI.this.waveformData.size()) + "\n").getBytes());

          for (Float waveformDatum : MainUI.this.waveformData) {
            sendSerialData((waveformDatum + "\n").getBytes());
          }

          SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
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
  private void addListenerForConnectSerialButton() {
    connectSerial.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        String port = (String) SerialPortsComboBox.getSelectedItem();

        serialConnect(e, port);
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

  private synchronized void sendSerialData(byte[] cmd) {
    if (serialIsConnected) {
      try {
        serialOutputStream.write(cmd);
        logger("[Serial] Sent '" + (new String(cmd, StandardCharsets.UTF_8)).trim());
      } catch (IOException ex) {
        logger("[Serial] Error sending '" + (new String(cmd, StandardCharsets.UTF_8)).trim());
      }
    } else {
      logger("Serial port not connected");
    }
  }

  private void serialConnect(ActionEvent e, String port) {
    switch (e.getActionCommand()) {
      case "Connect" -> {
        logger("Connecting to: " + port);
        SerialPort[] portList = SerialPort.getCommPorts();
        serialPort = portList[SerialPortsComboBox.getSelectedIndex()];
        serialPort.setBaudRate(500000);
        serialPort.flushIOBuffers();
        serialPort.openPort();
        serialIsConnected = serialPort.isOpen();
      }
      case "Disconnect" -> {
        serialIsConnected = false;
        serialPort.closePort();
      }
    }

    if (serialIsConnected) {
      connectedRadioButton.setEnabled(true);
      loadDataButton.setEnabled(true);
      serialOutputStream = serialPort.getOutputStream();
      connectSerial.setText("Disconnect");
      addSerialReadListener(serialPort);
    } else {
      connectedRadioButton.setEnabled(false);
      loadDataButton.setEnabled(false);
      runButton.setEnabled(false);
      connectSerial.setText("Connect");
    }
  }

  private void addSerialReadListener(SerialPort activePort) {
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
        String[] values = incomingLine.split(":");
        System.out.println(incomingLine + "\n");
        switch (values[0]) {
          case "E" -> logger("Error: " + values[1]);
          case "A" -> logger("Ack set motor speed: " + values[1]);
          case "D" -> {
            logger("Data loaded to microcontroller: " + values[1] + " lines");
            runButton.setEnabled(true);
          }
          case "I" -> logger("Info: " + values[1]);
          case "P" -> {
            double pval = Double.parseDouble(values[1]);
            SwingUtilities.invokeLater(new Runnable() {
              @Override
              public void run() {
                pressureSensorSeries.addOrUpdate(
                  new Millisecond(),
                  pval
                );
              }
            });
          }
          default -> System.out.println("Unexpected command: " + values[0] + " with value " + values[1]);
        }
      }
    });
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
    this.SerialPortsComboBox.removeAllItems();
    SerialPort[] portList = SerialPort.getCommPorts();
    for (SerialPort port : portList) {
      this.SerialPortsComboBox.addItem(port.getSystemPortName());
    }
    this.SerialPortsComboBox.setEnabled(true);
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
    pressureSensorSeries.setMaximumItemCount(110);
    TimeSeriesCollection pressureChartTimeSeriesCollection = new TimeSeriesCollection(pressureSensorSeries);

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
    plot.getRangeAxis().setAutoRange(true);

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
    PrimeSpeedSlider.setMaximum(200);
    PrimeSpeedSlider.setMinimum(-200);
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
    gbc = new GridBagConstraints();
    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.weightx = 0.5;
    gbc.weighty = 1.0;
    gbc.fill = GridBagConstraints.BOTH;
    ChartPanelContainer.add(pressureChartPanel, gbc);
    final JPanel panel1 = new JPanel();
    panel1.setLayout(new GridLayoutManager(8, 2, new Insets(0, 0, 0, 0), -1, -1));
    gbc = new GridBagConstraints();
    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.fill = GridBagConstraints.BOTH;
    rootPanel.add(panel1, gbc);
    deviceChooserLabel = new JLabel();
    deviceChooserLabel.setText("Choose device");
    panel1.add(deviceChooserLabel, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    connectSerial = new JButton();
    connectSerial.setText("Connect");
    panel1.add(connectSerial, new GridConstraints(1, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 1, false));
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
    panel1.add(runButton, new GridConstraints(4, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    loadDataButton = new JButton();
    loadDataButton.setEnabled(false);
    loadDataButton.setLabel("Load data");
    loadDataButton.setText("Load data");
    panel1.add(loadDataButton, new GridConstraints(4, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    timeStepMS = new JTextField();
    timeStepMS.setColumns(6);
    timeStepMS.setText("100");
    timeStepMS.setToolTipText("Number of ms between movement updates");
    panel1.add(timeStepMS, new GridConstraints(6, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
    timeStepMSButton = new JButton();
    timeStepMSButton.setText("Update");
    panel1.add(timeStepMSButton, new GridConstraints(6, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    final JLabel label1 = new JLabel();
    label1.setText("ms between updates");
    panel1.add(label1, new GridConstraints(5, 0, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    SerialPortsComboBox = new JComboBox();
    SerialPortsComboBox.setEnabled(true);
    final DefaultComboBoxModel defaultComboBoxModel1 = new DefaultComboBoxModel();
    defaultComboBoxModel1.addElement("not found");
    SerialPortsComboBox.setModel(defaultComboBoxModel1);
    SerialPortsComboBox.setToolTipText("Choose serial port");
    panel1.add(SerialPortsComboBox, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    final Spacer spacer1 = new Spacer();
    panel1.add(spacer1, new GridConstraints(7, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1, GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
    logScrollPane.setVerticalScrollBar(logScrollBar);
    deviceChooserLabel.setLabelFor(SerialPortsComboBox);
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
