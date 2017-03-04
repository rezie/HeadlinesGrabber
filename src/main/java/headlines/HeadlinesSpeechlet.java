/**
    Copyright 2014-2015 Amazon.com, Inc. or its affiliates. All Rights Reserved.

    Licensed under the Apache License, Version 2.0 (the "License"). You may not use this file except in compliance with the License. A copy of the License is located at

        http://aws.amazon.com/apache2.0/

    or in the "license" file accompanying this file. This file is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */
package headlines;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazon.speech.slu.Intent;
import com.amazon.speech.slu.Slot;
import com.amazon.speech.speechlet.IntentRequest;
import com.amazon.speech.speechlet.LaunchRequest;
import com.amazon.speech.speechlet.Session;
import com.amazon.speech.speechlet.SessionEndedRequest;
import com.amazon.speech.speechlet.SessionStartedRequest;
import com.amazon.speech.speechlet.Speechlet;
import com.amazon.speech.speechlet.SpeechletException;
import com.amazon.speech.speechlet.SpeechletResponse;
import com.amazon.speech.ui.OutputSpeech;
import com.amazon.speech.ui.PlainTextOutputSpeech;
import com.amazon.speech.ui.SsmlOutputSpeech;
import com.amazon.speech.ui.Reprompt;

import feed.Feed;
import feed.FeedMessage;
import feed.FeedParser;

public class HeadlinesSpeechlet implements Speechlet {
    private static final Logger log = LoggerFactory.getLogger(HeadlinesSpeechlet.class);
    private static final String CSV_DELIMITER = ",";
    private static final String RSS_URL = "https://github.com/rezie/HeadlinesGrabber/blob/master/rss.csv";
    private static Map<String, String> RSS_URLS;

    private static final String SLOT_SITE = "Site";


    @Override
    public void onSessionStarted(final SessionStartedRequest request, final Session session)
            throws SpeechletException {
        log.info("onSessionStarted requestId={}, sessionId={}", request.getRequestId(),
                session.getSessionId());

        TryParseRssFeeds();
    }

    private void TryParseRssFeeds() throws SpeechletException{
        BufferedReader br;

        // any initialization logic goes here
        try {
            // Grab the newest copy from github
            URL site = new URL(RSS_URL);
            InputStream in = site.openStream();
            br = new BufferedReader(new InputStreamReader(in));
            ParseRssFeeds(br);

        } catch (IOException ex) {
            try {
                // Fallback - use the packaged file
                InputStream in = new FileInputStream("rss.csv");
                br = new BufferedReader(new InputStreamReader(in));
                ParseRssFeeds(br);
            } catch (FileNotFoundException e1) {
                // The RSS file is neither available from github nor from the package for some strange reason
                br = null;
                throw new SpeechletException(e1);
            }
        }
    }

    private void ParseRssFeeds(BufferedReader br) throws SpeechletException {
        String curr;

        RSS_URLS = new HashMap<>();
        try{
            while((curr = br.readLine()) != null){
                String[] line = curr.split(CSV_DELIMITER);
                RSS_URLS.put(line[0],line[1]);
            }
        } catch (IOException e) {
            throw new SpeechletException(e);
        }
    }

    @Override
    public SpeechletResponse onLaunch(final LaunchRequest request, final Session session)
            throws SpeechletException {
        log.info("onLaunch requestId={}, sessionId={}", request.getRequestId(),
                session.getSessionId());

        return getWelcomeResponse();
    }

    @Override
    public SpeechletResponse onIntent(final IntentRequest request, final Session session)
            throws SpeechletException {
        log.info("onIntent requestId={}, sessionId={}", request.getRequestId(),
                session.getSessionId());

        Intent intent = request.getIntent();
        String intentName = intent.getName();

        if ("GetHeadlines".equals(intentName)) {
            return handleFirstEventRequest(intent, session);
        } else if ("AMAZON.HelpIntent".equals(intentName)) {
            // Create the plain text output.
            String speechOutput =
                    "You can ask for the headlines for supported sites, such as from the New York Times";
            String repromptText = "Which site would you like to hear from?";

            return newAskResponse(speechOutput, false, repromptText, false);
        } else if ("AMAZON.StopIntent".equals(intentName)) {
            PlainTextOutputSpeech outputSpeech = new PlainTextOutputSpeech();
            outputSpeech.setText("Goodbye");

            return SpeechletResponse.newTellResponse(outputSpeech);
        } else if ("AMAZON.CancelIntent".equals(intentName)) {
            PlainTextOutputSpeech outputSpeech = new PlainTextOutputSpeech();
            outputSpeech.setText("Goodbye");

            return SpeechletResponse.newTellResponse(outputSpeech);
        } else {
            throw new SpeechletException("Invalid Intent");
        }
    }

    @Override
    public void onSessionEnded(final SessionEndedRequest request, final Session session)
            throws SpeechletException {
        log.info("onSessionEnded requestId={}, sessionId={}", request.getRequestId(),
                session.getSessionId());

        // any session cleanup logic would go here
    }

    /**
     * Function to handle the onLaunch skill behavior.
     * 
     * @return SpeechletResponse object with voice/card response to return to the user
     */
    private SpeechletResponse getWelcomeResponse() {
        String speechOutput = "What sites would you like headlines for?";
        // If the user either does not reply to the welcome message or says something that is not
        // understood, they will be prompted again with this text.
        String repromptText =
                "With headlines grabber, you can ask for the headlines of various site. "
                        + " For example, you could say get me the headlines for The New York Times."
                        + " Now, which site do you want to hear from?";

        return newAskResponse(speechOutput, false, repromptText, false);
    }

    /**
     * Prepares the speech to reply to the user. Obtain events from Wikipedia for the date specified
     * by the user (or for today's date, if no date is specified), and return those events in both
     * speech and SimpleCard format.
     * 
     * @param intent
     *            the intent object which contains the date slot
     * @param session
     *            the session object
     * @return SpeechletResponse object with voice/card response to return to the user
     */
    private SpeechletResponse handleFirstEventRequest(Intent intent, Session session) {
        Slot site = intent.getSlot(SLOT_SITE);

        String siteName = site.getValue();
        String siteUrl = RSS_URLS.get(siteName.toLowerCase());
        String repromptText = "Which site would you like to hear from?";
        String output;

        if(siteUrl == null){
            output = "Sorry, the site you have requested is not currently supported. If you would like to request support, please notify the developer of this skill.";
        } else {
            output = "Here are your headlines from " + siteName;
            log.info("handleFirstEventRequest siteUrl={}", siteUrl);


            FeedParser parser = new FeedParser(siteUrl);
            Feed feed = parser.readFeed();

            for (FeedMessage message : feed.getMessages()) {
                log.info(message.getTitle());
                output += "<p>";
                output += message.getTitle();
                output += "</p>";
            }
        }
        
        SpeechletResponse response = newAskResponse("<speak>" + output + "</speak>", true, repromptText, false);
        return response;
    }

    /**
     * Wrapper for creating the Ask response from the input strings.
     * 
     * @param stringOutput
     *            the output to be spoken
     * @param isOutputSsml
     *            whether the output text is of type SSML
     * @param repromptText
     *            the reprompt for if the user doesn't reply or is misunderstood.
     * @param isRepromptSsml
     *            whether the reprompt text is of type SSML
     * @return SpeechletResponse the speechlet response
     */
    private SpeechletResponse newAskResponse(String stringOutput, boolean isOutputSsml,
            String repromptText, boolean isRepromptSsml) {
        OutputSpeech outputSpeech, repromptOutputSpeech;
        if (isOutputSsml) {
            outputSpeech = new SsmlOutputSpeech();
            ((SsmlOutputSpeech) outputSpeech).setSsml(stringOutput);
        } else {
            outputSpeech = new PlainTextOutputSpeech();
            ((PlainTextOutputSpeech) outputSpeech).setText(stringOutput);
        }

        if (isRepromptSsml) {
            repromptOutputSpeech = new SsmlOutputSpeech();
            ((SsmlOutputSpeech) repromptOutputSpeech).setSsml(repromptText);
        } else {
            repromptOutputSpeech = new PlainTextOutputSpeech();
            ((PlainTextOutputSpeech) repromptOutputSpeech).setText(repromptText);
        }
        Reprompt reprompt = new Reprompt();
        reprompt.setOutputSpeech(repromptOutputSpeech);
        return SpeechletResponse.newAskResponse(outputSpeech, reprompt);
    }

}
