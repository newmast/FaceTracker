#include <Stepper.h>
#include <SoftwareSerial.h>
 
const int stepsPerRevolution = 20;
 
Stepper myStepper(stepsPerRevolution, 8, 9, 10, 11);            
SoftwareSerial bluetooth(0, 1);

int stepCount = 0;
char data = 0;

void setup() {
  bluetooth.begin(115200);
}
 
void loop() {
  if(bluetooth.available()) {
    data = bluetooth.read();

    bluetooth.print("I received: ");
    bluetooth.print(data);
      
    if(data == '1') {
      myStepper.step(1);
    } else if(data == '2') {
      myStepper.step(-1);
    }
      
    delay(300);
  }
}