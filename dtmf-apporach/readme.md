# Gathering input via DTMF with media streams

## Setup

### Custom Endpoint to handle captured DTMF input/ no input

This endpoint should:
1. Check the outcome of gather by looking at "Digits" parameter
2. If found, return TwiML to initiate a new stream and pass contextual parameters (to connect to the previous/Original session) along with captured input 
2.B If not found, decide to either replay the message (with logic for max retries) or connect with server to determine next steps 


## Usage

When you need to gather user input, update the TwiML of the original call using callSid,
Sample TwiML as below:

<Response>
  <Gather action="<PONT TO ABOVE ENDPOINT>" method="POST" input="dtmf" timeout="3" numDigits="1">
    <Say>Please press one digit on your keypad.
    </Say>
  </Gather>
  <Redirect method="POST"><PONT TO ABOVE ENDPOINT></Redirect>
</Response>
