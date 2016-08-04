/* i2c_ph: monitor pH readings from an i2c enabled Atlas chip.
 *
 * Based on Atlas's Arduino examples and the WiringPi documentation.
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <errno.h>
#include <readline/readline.h>
#include <signal.h>

#include <wiringPi.h>
#include <wiringPiI2C.h>

#define MSG_END '\0'
#define BUFFER_SIZE 1 << 8

#define RX_NO_DATA 255
#define RX_PENDING 254
#define RX_FAILED 2
#define RX_READY 1

#define PROMPT "pH> "

#define I2C_ADDRESS 99

int write_string(int fd, char* str);
int read_string(int fd, char* buf, int max_len);
int read_int(int fd);

static void handle_sigint(int sig) {
  fprintf(stderr, "Exiting.");
}

int main(int argc, char** argv) {
  if (signal(SIGINT, handle_sigint) == SIG_ERR) {
    fprintf(stderr, "Unable to install interrupt handler, exiting.\n");
    return 1;
  }

  int fd = wiringPiI2CSetup(I2C_ADDRESS);

  if (fd == -1) {
    int err = errno;
    fprintf(stderr, "Unable to initialize connection to I2C device: %s\n", strerror(err));
    return 1;
  }

  printf("Set up wiringPi with dev id %d, resulting fd number is %d\n", I2C_ADDRESS, fd);

  char* input = NULL;
  char *buffer = malloc(sizeof(char) * BUFFER_SIZE);
  if (buffer == NULL) {
    fprintf(stderr, "Unable to allocate buffer for reading, exiting.\n");
    return 1;
  }

  do {
    // readline() mallocs new string buffers with each line, so free before reading.
    if (input != NULL) {
      free(input);
    }
    input = readline(PROMPT);
    // input should be null if the user types ctrl-d.
    if (input == NULL) {
      break;
    }

    if (strcmp("exit", input) == 0 || strcmp("quit", input) == 0) {
      break;
    }

    if (strcmp("", input) == 0) {
      continue;
    }

    // TODO: add a help message.

    // Replace any captured newline with a message terminator.
    char* newline = strchr(input, '\n');
    if (newline != NULL && *newline != '\0') {
      *newline = '\0';
    }

    int len = strlen(input);
    int written_bytes = write_string(fd, input);
    if (len != written_bytes) {
      fprintf(stderr, "ERROR: wrote only %d of %d bytes\n", written_bytes, len);
    }

    if (input[0] == 'r' || input[0] == 'R') {
      delay(2000); // Delay 1000 ms for a pH reading.
    } else if (input[0] == 'c' || input[0] == 'C') {
      delay(2000); // Calibration ops need 1.8s to complete.
    } else {
      delay(1000); // Delay 300 ms for all other operations.
    }

    int status = read_int(fd);

    switch (status) {
      case RX_NO_DATA:
        printf("No data available.\n");
        break;
      case RX_PENDING:
        printf("Action pending\n");
        break;
      case RX_FAILED:
        printf("Action failed\n");
        break;
      case RX_READY:
        printf("Action ready\n");
        buffer[0] = '\0';
        int i;
        for (i = 0; i < BUFFER_SIZE; i++) {
          int iv = wiringPiI2CRead(fd);
          fprintf(stderr, " < %d\n", iv);
          char c = (char) iv;
          buffer[i] = c;
          if (c == '\0') {
            break;
          }
        }
        printf("  <[(%d)] %s\n", i, buffer);
        // TODO: do better than this simple reporting.
        break;
    }

    /*
    if (status == RX_READY) {
      buffer[0] = '\0';
      int bytes_read = read_string(fd, buffer, BUFFER_SIZE);
      printf("  <[(%d)] %s\n", bytes_read, buffer);
    }
    */

  } while (input != NULL);

  if (input != NULL) {
    free(input);
  }
  if (buffer != NULL) {
    free(buffer);
  }

  printf("Exiting.\n");

  // TOOD: continue here.
  return 0;
}

/* ************************************************
 * Helper functions
 */

int write_char(int fd, char c) {
  int res, ci;
  ci = (int) c;
  fprintf(stderr, "Writing %d\n", ci);
  res = wiringPiI2CWrite(fd, ci);
  //if (res != 1) {
  //  fprintf(stderr, "wiringPiI2CWrite returned unexpected result code: %d\n", res);
  //}
  return res;
}

// Writes null-terminated command strings.  Returns number of bytes written.
int write_string(int fd, char* str) {
  int i, len;
  len = strlen(str);
  // Use <= len to ensure null character is also written.
  for (i = 0; i <= len; i++) {
    write_char(fd, str[i]);
  }
  return len;
}

char read_char(int fd) {
  int res = wiringPiI2CRead(fd);
  fprintf(stderr, " < %d\n", res);
  return (char) res;
}

int read_int(int fd) {
  return wiringPiI2CRead(fd);
}

int read_string(int fd, char* buf, int max_len) {
  int i, end = max_len - 1;
  char c = 0;
  for (i = 0; i < end; i++) {
    c = read_char(fd);
    buf[i] = c;
    // Also consume and store the terminating null character.
    if (c == MSG_END) {
      break;
    }
  }
  return i; // Don't bother to add the message terminator to the length.
}
