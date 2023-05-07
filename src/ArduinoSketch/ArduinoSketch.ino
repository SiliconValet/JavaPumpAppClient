
#include <Wire.h>
#include "Adafruit_MPRLS.h"
#include <AccelStepper.h>

// Relevant docs:
// http://www.airspayce.com/mikem/arduino/AccelStepper/classAccelStepper.html

// You don't *need* a reset and EOC pin for most uses, so we set to -1 and don't connect
#define RESET_PIN  -1  // set to any GPIO pin # to hard-reset on begin()
#define EOC_PIN    -1  // set to any GPIO pin to read end-of-conversion by pin
Adafruit_MPRLS mpr = Adafruit_MPRLS(RESET_PIN, EOC_PIN);

const byte numChars = 32;
char receivedChars[numChars];   // an array to store the received data

boolean newData = false;
boolean fatalError = false;

unsigned long startMillis = millis();

// defines pins numbers
const int stepPin = 5;
const int directionPin = 6;
const int enablePin = 7;

// Define a stepper and the pins it will use
// 1 or AccelStepper::DRIVER means a stepper driver (with Step and Direction pins)
AccelStepper stepper(AccelStepper::DRIVER, stepPin, directionPin);

int previousVelocity = 0;

void setup() {
  Wire.begin();
  Serial.begin(500000);
  delay(1000);

  stepper.setEnablePin(enablePin);
  stepper.setPinsInverted(false,false,true);
  stepper.enableOutputs();
  stepper.setAcceleration(100.0);
  stepper.setMaxSpeed(1000.0);
  stepper.setSpeed(0);

  // scanI2c();

  // Initialize and test pressure sensor.
  if (!mpr.begin()) {
    Serial.println("E:Failed connect to pressure sensor");
    fatalError = true;
  }


  // If we've got a fatal error, stop here.
  if (fatalError) {
    while (1);
  }

  // Send the initial "ack" to kick off the event loop.
  Serial.println("A:0");
}

void loop() {
  receiveWithEndMarker();
  stepper.runSpeed();
  showNewData();
}

void receiveWithEndMarker() {
  static byte ndx = 0;
  char endMarker = '\n';
  char rc;

  while (Serial.available() > 0 && newData == false) {
    rc = Serial.read();

    // Sneak in a stepper iteration since we might be here a few loops.
    stepper.runSpeed();

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
    }
  }
}

void showPressureSensorUpdate() {
  float pressure_hPa = mpr.readPressure();
  static unsigned long lastUpdate = 0;
  const unsigned long currentTimeMs = millis();

  // Send an update from the pressure sensor at interval.
  if ((currentTimeMs - lastUpdate) > 250) {
    lastUpdate = millis();
    //Serial.print("P:");
    // Convert to mmHG 1 hPa to mmHG = 0.75006 mmHG
    Serial.println(strcat("P:", pressure_hPa * 0.7500615613));
  }
}

void showNewData() {
  int val;
  char *cmd;

  // If new data is available for review.
  if (newData == true) {
    // Tokenize the string from S:123 to get the parts.
    cmd = strtok(receivedChars, ":");

    // If the command is "S" (Set speed)
    if (strcmp(cmd,'S')) {
      // Convert the numeric bit to an integer.
      val = atoi(strtok(NULL, ":"));
      // If the velocity has changed, set new speed and ack.
      if (val != previousVelocity) {
        previousVelocity = val;
        stepper.enableOutputs();

        stepper.setSpeed(val);
        //Serial.print("A:");
        Serial.println(strcat("A:", val));
        if (val == 0) {
          stepper.disableOutputs();
        }
      }

      //Serial.print(":");
      //Serial.println( millis() );
    } else {
      //Serial.print("E:Received unknown command: ");
      Serial.println(strcat("E:Received unknown command: ", cmd));
    }
    newData = false;
  }
}

// Set I2C bus to use: Wire, Wire1, etc.
#define WIRE Wire

// https://learn.adafruit.com/scanning-i2c-addresses/arduino?gclid=Cj0KCQiAo-yfBhD_ARIsANr56g7t2cx1Zpw6CqP6XM_Df1AmBVJSP-WiBSNGAYtv5QO409IXUgFtabsaAjckEALw_wcB
void scanI2c() {
  byte error, address;
  int nDevices;

  Serial.println("I:Scanning...");

  nDevices = 0;
  for (address = 1; address < 127; address++) {
    // The i2c_scanner uses the return value of
    // the Write.endTransmisstion to see if
    // a device did acknowledge to the address.
    WIRE.beginTransmission(address);
    error = WIRE.endTransmission();

    if (error == 0) {
      Serial.print("I:I2C device found at address 0x");
      if (address < 16)
        Serial.print("0");
      Serial.print(address, HEX);
      Serial.println("  !");

      nDevices++;
    } else if (error == 4) {
      Serial.print("I:Unknown error at address 0x");
      if (address < 16)
        Serial.print("0");
      Serial.println(address, HEX);
    }
  }

  if (nDevices == 0)
    Serial.println("E:No I2C devices found\n");
  else
    Serial.println("I:done\n");

}