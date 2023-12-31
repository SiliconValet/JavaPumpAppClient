
#include <Wire.h>
#include "Adafruit_MPRLS.h"
#include <AccelStepper.h>

// Relevant docs:
// http://www.airspayce.com/mikem/arduino/AccelStepper/classAccelStepper.html

// You don't *need* a reset and EOC pin for most uses, so we set to -1 and don't connect
#define RESET_PIN  -1  // set to any GPIO pin # to hard-reset on begin()
#define EOC_PIN    -1  // set to any GPIO pin to read end-of-conversion by pin
// Set I2C bus to use: Wire, Wire1, etc.
#define WIRE Wire
Adafruit_MPRLS mpr = Adafruit_MPRLS(RESET_PIN, EOC_PIN);

const byte numChars = 32;
char receivedChars[numChars];   // an array to store the received data

boolean newData = false;
boolean fatalError = false;
boolean priming = false;
// Whether or not to run the motors.
boolean runMotors = false;
// Whether to show debugging.
boolean debugging = false;

// defines pins numbers
const int stepPin = 5;
const int directionPin = 6;
const int enablePin = 7;

#define positionalDataMaxElements 200

int positionalDataCount = 0;
float positionalData[positionalDataMaxElements];
int primingSpeed = 0;
int positionalDataIndex = 0;
//int dataTimeStepMS = 8;
int dataTimeStepMS = 100;
float scaleMultiplier = 1.0;

// Define a stepper and the pins it will use
// 1 or AccelStepper::DRIVER means a stepper driver (with Step and Direction pins)
AccelStepper stepper(AccelStepper::DRIVER, stepPin, directionPin);

int previousVelocity = 0;


void setup() {
  Wire.begin();
  Serial.begin(500000);
  //Serial.begin(19200);
  delay(1000);

  stepper.setEnablePin(enablePin);
  stepper.setPinsInverted(false,false,true);
  stepper.enableOutputs();
  stepper.setAcceleration(1500.0);
  stepper.setMaxSpeed(1500.0);
  stepper.setSpeed(0);

  // Initialize and test pressure sensor.
  if (!mpr.begin()) {
    Serial.println(F("E:Failed connect to pressure sensor"));
    fatalError = true;
  }

  // Send the initial "ack" to kick off the event loop.
  Serial.println(F("A:0"));
}

void loop() {
  // Fetch data from serial and process it.
  processInput();

  // Update runtime state and feedback.
  if (runMotors == true)  {
    showPressureSensorUpdate();
    updateTargetPosition();
  }

  if (priming == true) {
    showPressureSensorUpdate();
    updatePrimingPosition();
  }

  // Update stepper position if motors should be active.
  if (runMotors || priming) {
    stepper.run();
  }
}

void updatePrimingPosition() {
  static unsigned long lastUpdate = 0;
  const unsigned long currentTimeMs = millis();

  // If we have waited long enough, update the target position.
  if ((currentTimeMs - lastUpdate) > dataTimeStepMS) {
    lastUpdate = millis();

    // Update the target position as current + offset;
    stepper.moveTo(stepper.currentPosition()+primingSpeed);

    // Debugging
    // Serial.print("I:");
    // Serial.print(stepper.currentPosition());
    // Serial.print(" -> ");
    // Serial.println(stepper.currentPosition()+primingSpeed);
  }
}

void updateTargetPosition() {
  static unsigned long lastUpdate = 0;
  const unsigned long currentTimeMs = millis();
  int targetPos = 0;

  // If we have positional data to process.
  if (positionalDataCount > 0) {
    // If we have waited long enough, update the target position.
    if ((currentTimeMs - lastUpdate) > dataTimeStepMS) {
      lastUpdate = millis();
      // Update the target position. The int cast always truncates, so the +0.5 is
      // a but of a hack to get it to round to nearest int.
      targetPos = (int) ((positionalData[positionalDataIndex] * scaleMultiplier) + 0.5);
      stepper.moveTo(targetPos);
      
      if (debugging) {
        // Debugging.
        Serial.print(F("I:CurrentPos "));
        Serial.print(stepper.currentPosition());
        Serial.print(F(" TargetPos "));
        Serial.print(targetPos);
        Serial.print(F(" RawTarget "));
        Serial.print(positionalData[positionalDataIndex] * scaleMultiplier);
        Serial.print(F(" A "));
        Serial.print(stepper.acceleration(), 3);
        Serial.print(F(" V "));
        Serial.println(stepper.speed(), 3);
      }
      
      // Increment the index.
      positionalDataIndex++;
      // If we have reached the end of the data, stop the motors.
      if (positionalDataIndex >= positionalDataCount) {
        positionalDataIndex = 0;
        Serial.println(F("I:Iteration complete."));
      }
    }
  }
}

/**
 * Receive a string from the serial port and store it in a buffer.
 * The string is terminated by a newline character.
 */
void receiveWithEndMarker() {
  static byte ndx = 0;
  char endMarker = '\n';
  char rc;

  while (Serial.available() > 0 && newData == false) {
    rc = Serial.read();

    if (rc != endMarker) {
      receivedChars[ndx] = rc;
      ndx++;
      if (ndx >= numChars) {
        ndx = numChars - 1;
      }
    } else {
      receivedChars[ndx] = '\0'; // terminate the string
      ndx = 0;
      newData = true;
      //Serial.print("I:");
      //Serial.println(receivedChars);
    }
  }
}

/**
 * Load data from the serial port.
 * Last line in the data feed will be empty.
 */
void loadPositionalDataFromSerial(int numRows) {
  // Stop all motor movement.
  runMotors = false;
  priming = false;
  int i = 0;
  byte ndx = 0;
  // Re initialize the count of lines.
  positionalDataCount = 0;
  // Reset the index for the iterator,
  positionalDataIndex = 0;
  const char endMarker = '\n';
  char rc;
  bool processing = true;

  for (i=0; i<numRows; i++) {
    processing = true;
    while (processing == true) {
      if (Serial.available()) {
        rc = Serial.read();

        if (rc != endMarker) {
          receivedChars[ndx] = rc;
          ndx++;
          if (ndx >= numChars) {
            ndx = numChars - 1;
          }
        } else {
          receivedChars[ndx] = '\0'; // terminate the string
          ndx = 0;
          processing = false;
        }
      }
    }

    // Debugging
    // if (i == 0) {
    //   Serial.print("I:First item received ");
    //   Serial.println(receivedChars);
    // }

    // Hedge against exceeding max elements.
    if (i <= positionalDataMaxElements) {
      positionalData[i] = atof(receivedChars);
    }
    else {
      Serial.println(F("E:Exceeded maximum allowed data."));
    }
  }

  // Debugging
  // Serial.print("I:Last item received ");
  // Serial.println(receivedChars);

  // Debugging.
  // for (i=0; i<numRows; i++) {
  //   Serial.print(F("I:Data imported "));
  //   Serial.print(i);
  //   Serial.print(" ");
  //   Serial.println(positionalData[i], 6);
  // }

  positionalDataCount = i;

  // Inform controller of data load.
  Serial.print(F("D:"));
  Serial.println(i);
  
  setNewHomePosition();
}

/**
 * Show the current pressure sensor reading.
 */
void showPressureSensorUpdate() {
  if (debugging) {
    return;
  }
  float pressure_hPa = mpr.readPressure();
  float pressure_mmHg = 0;
  char* tmp;
  static unsigned long lastUpdate = 0;
  const unsigned long currentTimeMs = millis();

  // Send an update from the pressure sensor at interval.
  if ((currentTimeMs - lastUpdate) > 250) {
    lastUpdate = millis();
    // Convert to mmHG 1 hPa to mmHG = 0.75006 mmHG
    Serial.print("P:");
    Serial.println(pressure_hPa * 0.7500615613);
  }
}

/**
 * Provide updates via serial.
 */
void processInput() {
  char cmd;

  // Check for data from serial and fill buffer.
  receiveWithEndMarker();

  // If new data is available for review.
  if (newData == true) {

    // Tokenize the string like 'S:123' to get the parts.
    switch (receivedChars[0]) {
      case 'D':
        // Pull and toss the first token.
        strtok(receivedChars, ":");
        if (strcmp(strtok(NULL, ":"),"T")) {
          debugging = true;
          Serial.println(F("I:Debugging enabled."));
        } 
        else {
          debugging = false;
          Serial.println(F("I:Debugging disabled."));
        }
        break;
      // Update (F)requency
      case 'F':
        // Pull and toss the first token.
        strtok(receivedChars, ":");
        // Convert the numeric bit to an integer.
        updateStepFrequency(atoi(strtok(NULL, ":")));
        break;
      case 'H':
        setNewHomePosition();
        break;
      // (L)oad data
      case 'L': 
        // Pull and toss the first token.
        strtok(receivedChars, ":");
        // Convert the numeric bit to an integer.
        loadPositionalDataFromSerial(atoi(strtok(NULL, ":")));
        break;
      // (P)rime pump
      case 'P':
        priming = true;
        // Pull and toss the first token.
        strtok(receivedChars, ":");
        // Convert the numeric bit to an integer.
        updatePriming(atoi(strtok(NULL, ":")));
        Serial.println(F("I:Priming"));
        break;
      // (R)un application.
      case 'R':
        runMotors = true;
        Serial.println(F("I:Application started"));
        break;
      // Stop application.
      case 'S':
        runMotors = false;
        priming = false;
        Serial.println(F("I:Application stopped"));
        break;
      case 'V':
        // Pull and toss the first token.
        strtok(receivedChars, ":");
        // Convert the numeric bit to an integer.
        updateVelocity(atoi(strtok(NULL, ":")));
        Serial.println(F("I:Velocity updated"));
        break;
      // Update scale multiplier for positional data
      case 'X':
        // Pull and toss the first token.
        strtok(receivedChars, ":");
        updateScaleMultiplier(atoi(strtok(NULL, ":")));
        break;
      // Error.
      case 'E':
      default: 
        Serial.print(F("E:Received unknown command: "));
        Serial.println(cmd);
        break;
    }

    // Reset newData to false until we receive something.
    newData = false;
  }
}

void setNewHomePosition() {
  stepper.setCurrentPosition(0);
}

void updatePriming(int val) {
  primingSpeed = -val;
  Serial.print(F("I:P:"));
  Serial.println(primingSpeed);
}

void updateScaleMultiplier(int val) {
  scaleMultiplier = val;
  Serial.print(F("I:Scale multiplier updated"));
  Serial.println(scaleMultiplier, 3);
}

void updateStepFrequency(int val) {
  dataTimeStepMS = (1/val)*1000;
  Serial.print(F("I:Step frequency updated"));
  Serial.println(dataTimeStepMS, 3);
}

void updateVelocity(int val) {
  // If the velocity has changed, set new speed and ack.
  if (val != previousVelocity) {
    previousVelocity = val;
    stepper.enableOutputs();

    stepper.setSpeed(val);
    Serial.print("A:");
    Serial.println(val);
    if (val == 0) {
      stepper.disableOutputs();
    }
  }
}

// https://learn.adafruit.com/scanning-i2c-addresses/arduino?gclid=Cj0KCQiAo-yfBhD_ARIsANr56g7t2cx1Zpw6CqP6XM_Df1AmBVJSP-WiBSNGAYtv5QO409IXUgFtabsaAjckEALw_wcB
void scanI2c() {
  byte error, address;
  int nDevices;

  Serial.println(F("I:Scanning..."));

  nDevices = 0;
  for (address = 1; address < 127; address++) {
    // The i2c_scanner uses the return value of
    // the Write.endTransmisstion to see if
    // a device did acknowledge to the address.
    WIRE.beginTransmission(address);
    error = WIRE.endTransmission();

    if (error == 0) {
      Serial.print(F("I:I2C device found at address 0x"));
      if (address < 16)
        Serial.print("0");
      Serial.print(address, HEX);
      Serial.println(F("  !"));

      nDevices++;
    } else if (error == 4) {
      Serial.print(F("I:Unknown error at address 0x"));
      if (address < 16)
        Serial.print("0");
      Serial.println(address, HEX);
    }
  }

  if (nDevices == 0)
    Serial.println(F("E:No I2C devices found\n"));
  else
    Serial.println(F("I:done\n"));

}
