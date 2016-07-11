/* An Arduino module that communicates with an Atlas pH monitor chip via I2C.
 *
 * Based on sample code from Atlas: http://www.atlas-scientific.com/_files/code/ph-i2c.pdf
 */

/* Note: the Diecimila uses analog pin 4 for I2C SDA and analog pin 5 for SCL.
 * Connect TX to analog 4 and RX to analog 5.
 */

#include <Wire.h>
#define I2C_ADDRESS 99
#define BUFFER_SIZE 64

#define RX_NO_DATA 255
#define RX_PENDING 254
#define RX_FAILED 2
#define RX_READY 1

char hostBuffer[BUFFER_SIZE];
char probeBuffer[BUFFER_SIZE];
boolean hostMessageReady = false;
boolean probeMessageReady = false;

byte responseCode = 0;
int delayTime = 1800;

void setup() {
  // put your setup code here, to run once:

  Serial.begin(9600);
  while (!Serial) {
    ; // Busy wait for serial connection to host PC to be ready.
  }
  Wire.begin();

  pinMode(13, OUTPUT);

  Serial.println("I2C ready");
}

void serialEvent() {
  byte bytesRead = Serial.readBytesUntil('\r', hostBuffer, BUFFER_SIZE);
  // Replace terminating \r with null.
  hostBuffer[bytesRead] = '\0';
  hostMessageReady = true;
}

void loop() {
  // put your main code here, to run repeatedly:
  if (!hostMessageReady) {
    return;
  }

  if (hostBuffer[0] == 'c' || hostBuffer[0] == 'C' ||
      hostBuffer[0] == 'r' || hostBuffer[0] == 'R') {
    delayTime = 1800; // Calibration and read ops take 1800ms.
  } else {
    delayTime = 300; // Minimum delay time is 300 for data to be ready.
  }

  digitalWrite(13, HIGH);
  Wire.beginTransmission(I2C_ADDRESS);
  Wire.write(hostBuffer);
  Wire.endTransmission();
  delay(delayTime);
  digitalWrite(13, LOW);

  Wire.requestFrom(I2C_ADDRESS, BUFFER_SIZE);
  responseCode = Wire.read();

  // TODO: act on response codes rather than just reporting.
  switch (responseCode) {
    case RX_NO_DATA:
      Serial.println("No data available");
      break;
    case RX_PENDING:
      Serial.println("Action pending");
      break;
    case RX_FAILED:
      Serial.println("Action failed");
      break;
    case RX_READY:
      Serial.println("Ready to receive");
      break;
  }

  if (responseCode == RX_READY) {
    for (int i = 0; i < BUFFER_SIZE && Wire.available(); i++) {
      char c = Wire.read();
      probeBuffer[i] = c;
      if (c == '\0') {
        Wire.endTransmission();
        break;
      }
    }

    Serial.println(probeBuffer);
  }

  hostMessageReady = false;
}
