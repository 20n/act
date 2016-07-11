/* An Arduino module that communicates with an Atlas pH monitor chip via TTL UART.
 *
 * Based on sample code from Atlas: http://www.atlas-scientific.com/_files/code/Arduino-Uno-pH-sample-code.pdf
 */

/* Note: the wiring diagram in the Atlas sample document is backwards.  Connect arduino pin 10 to
 * the RX terminal on the Atlast chip, and pin 11 to the TX terminal on the atlas chip.  Otherwise
 * nothing will happen.
 */

#include <SoftwareSerial.h>
#define PH_TX 10
#define PH_RX 11

SoftwareSerial phProbe(PH_RX, PH_TX);

String hostBuffer = "";
String probeBuffer = "";
boolean hostMessageReady = false;
boolean probeMessageReady = false;


void setup() {
  // put your setup code here, to run once:

  Serial.begin(9600);
  while (!Serial) {
    ; // Busy wait for serial connection to host PC to be ready.
  }
  phProbe.begin(9600);

  // Reserve memory for I/O string buffering.
  hostBuffer.reserve(64);
  probeBuffer.reserve(64);

  pinMode(13, OUTPUT);

  Serial.println("UART ready");
}

void serialEvent() {
  hostBuffer = Serial.readStringUntil('\r');
  hostMessageReady = true;
}

void loop() {
  // put your main code here, to run repeatedly:
  if (hostMessageReady) {
    phProbe.print(hostBuffer);
    phProbe.print('\r');
    Serial.println(hostBuffer);
    hostBuffer = "";
    hostMessageReady = false;
  }

  if (phProbe.available() > 0) {
    digitalWrite(13, HIGH);
    char c = phProbe.read();
    probeBuffer += c;
    if (c == '\r') {
      Serial.println(probeBuffer);
      probeBuffer = "";
    }
    delay(100);
    digitalWrite(13, LOW);
    delay(100);
  }
}
