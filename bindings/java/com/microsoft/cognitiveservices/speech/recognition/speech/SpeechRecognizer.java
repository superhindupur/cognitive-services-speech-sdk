package com.microsoft.cognitiveservices.speech.recognition.speech;
//
// Copyright (c) Microsoft. All rights reserved.
// Licensed under the MIT license. See LICENSE.md file in the project root for full license information.
//

import java.io.IOException;

import com.microsoft.cognitiveservices.speech.ParameterCollection;
import com.microsoft.cognitiveservices.speech.recognition.ParameterNames;
import com.microsoft.cognitiveservices.speech.recognition.RecognitionErrorEventArgs;
import com.microsoft.cognitiveservices.speech.recognition.translation.TranslationTextResult;
import com.microsoft.cognitiveservices.speech.util.EventHandler;
import com.microsoft.cognitiveservices.speech.util.EventHandlerImpl;
import com.microsoft.cognitiveservices.speech.util.Task;
import com.microsoft.cognitiveservices.speech.util.TaskRunner;

/**
  * Performs speech recognition from microphone, file, or other audio input streams, and gets transcribed text as result.
  * 
  */
public final class SpeechRecognizer extends com.microsoft.cognitiveservices.speech.recognition.Recognizer
{
    /**
      * The event IntermediateResultReceived signals that an intermediate recognition result is received.
      */
    final public EventHandlerImpl<SpeechRecognitionResultEventArgs> IntermediateResultReceived = new EventHandlerImpl<SpeechRecognitionResultEventArgs>();

    /**
      * The event FinalResultReceived signals that a final recognition result is received.
      */
    final public EventHandlerImpl<SpeechRecognitionResultEventArgs> FinalResultReceived = new EventHandlerImpl<SpeechRecognitionResultEventArgs>();

    /**
      * The event RecognitionErrorRaised signals that an error occurred during recognition.
      */
    final public EventHandlerImpl<RecognitionErrorEventArgs> RecognitionErrorRaised = new EventHandlerImpl<RecognitionErrorEventArgs>();

    /**
      * SpeechRecognizer constructor.
      * @param recoImpl The recognizer implementation
      */
    public SpeechRecognizer(com.microsoft.cognitiveservices.speech.internal.SpeechRecognizer recoImpl) throws UnsupportedOperationException
    {
        this.recoImpl = recoImpl;

        intermediateResultHandler = new ResultHandlerImpl(this, /*isFinalResultHandler:*/ false);
        recoImpl.getIntermediateResult().addEventListener(intermediateResultHandler);

        finalResultHandler = new ResultHandlerImpl(this, /*isFinalResultHandler:*/ true);
        recoImpl.getFinalResult().addEventListener(finalResultHandler);

        errorHandler = new ErrorHandlerImpl(this);
        recoImpl.getNoMatch().addEventListener(errorHandler);
        recoImpl.getCanceled().addEventListener(errorHandler);

        recoImpl.getSessionStarted().addEventListener(sessionStartedHandler);
        recoImpl.getSessionStopped().addEventListener(sessionStoppedHandler);
        recoImpl.getSpeechStartDetected().addEventListener(speechStartDetectedHandler);
        recoImpl.getSpeechEndDetected().addEventListener(speechEndDetectedHandler);

        _Parameters = new ParameterCollection<SpeechRecognizer>(this);
    }

    /**
      * Gets the deployment id of a customized speech model that is used for speech recognition.
      * @return the deployment id of a customized speech model that is used for speech recognition.
      */
    public String getDeploymentId()
    {
        return _Parameters.getString(ParameterNames.SpeechModelId);
    }
    
    /**
      * Sets the deployment id of a customized speech model that is used for speech recognition.
      * @param value The deployment id of a customized speech model that is used for speech recognition.
      */
    public void setDeploymentId(String value)
    {
        _Parameters.set(ParameterNames.SpeechModelId, value);
    }

    /**
      * Gets the spoken language of recognition.
      * @return The spoken language of recognition.
      */
    public String getLanguage()
    {
        return _Parameters.getString(ParameterNames.SpeechRecognitionLanguage);
    }

    /**
      * Sets the spoken language of recognition.
      * @param value The spoken language of recognition.
      */
    public void setLanguage(String value)
    {
        _Parameters.set(ParameterNames.SpeechRecognitionLanguage, value);
    }

    /**
      * The collection of parameters and their values defined for this SpeechRecognizer.
      * @return The collection of parameters and their values defined for this SpeechRecognizer.
      */
    public ParameterCollection<SpeechRecognizer> getParameters()
    {
        return _Parameters;
    }// { get; }
    private ParameterCollection<SpeechRecognizer> _Parameters;

    /**
      * Starts speech recognition, and stops after the first utterance is recognized. The task returns the recognition text as result.
      * @return A task representing the recognition operation. The task returns a value of SpeechRecognitionResult
      */
    public Task<SpeechRecognitionResult> recognizeAsync()
    {
        Task<SpeechRecognitionResult> t = new Task<SpeechRecognitionResult>(new TaskRunner() {
            SpeechRecognitionResult result;
            
            @Override
            public void run() {
                result = new SpeechRecognitionResult(recoImpl.recognize()); 
            }

            @Override
            public Object result() {
                return result;
            }});
        
        return t;
    }

    /**
      * Starts speech recognition on a continous audio stream, until stopContinuousRecognitionAsync() is called.
      * User must subscribe to events to receive recognition results.
      * @return A task representing the asynchronous operation that starts the recognition.
      */
    public Task<?> startContinuousRecognitionAsync()
    {
        Task<?> t = new Task(new TaskRunner() {

            @Override
            public void run() {
                recoImpl.startContinuousRecognitionAsync();
            }

            @Override
            public Object result() {
                return null;
            }});
        
        return t;
    }

    /**
      * Stops continuous speech recognition.
      * @return A task representing the asynchronous operation that stops the recognition.
      */
    public Task<?> stopContinuousRecognitionAsync()
    {
        Task<?> t = new Task(new TaskRunner() {

            @Override
            public void run() {
                recoImpl.stopContinuousRecognitionAsync();
            }

            @Override
            public Object result() {
                return null;
            }});
        
        return t;
    }

    /**
      * Starts speech recognition on a continous audio stream with keyword spotting, until stopKeywordRecognitionAsync() is called.
      * User must subscribe to events to receive recognition results.
      * @param keyword The keyword to recognize.
      * @return A task representing the asynchronous operation that starts the recognition.
      */
    public Task<?> startKeywordRecognitionAsync(String keyword)
    {
        Task<?> t = new Task(new TaskRunner() {

            @Override
            public void run() {
                recoImpl.startKeywordRecognition(keyword);
            }

            @Override
            public Object result() {
                return null;
            }});
        
        return t;
    }

    /**
      * Stops continuous speech recognition.
      * @return A task representing the asynchronous operation that stops the recognition.
      */
    public Task<?> stopKeywordRecognitionAsync()
    {
        Task<?> t = new Task(new TaskRunner() {

            @Override
            public void run() {
                recoImpl.stopKeywordRecognition();
            }

            @Override
            public Object result() {
                return null;
            }});
        
        return t;
    }
    
    @Override
    protected void dispose(boolean disposing) throws IOException
    {
        if (disposed)
        {
            return;
        }

        if (disposing)
        {
            getRecoImpl().getIntermediateResult().removeEventListener(intermediateResultHandler);
            getRecoImpl().getFinalResult().removeEventListener(finalResultHandler);
            getRecoImpl().getNoMatch().removeEventListener(errorHandler);
            getRecoImpl().getCanceled().removeEventListener(errorHandler);
            getRecoImpl().getSessionStarted().removeEventListener(sessionStartedHandler);
            getRecoImpl().getSessionStopped().removeEventListener(sessionStoppedHandler);
            getRecoImpl().getSpeechStartDetected().removeEventListener(speechStartDetectedHandler);
            getRecoImpl().getSpeechEndDetected().removeEventListener(speechEndDetectedHandler);

            intermediateResultHandler.delete();
            finalResultHandler.delete();
            errorHandler.delete();
            getRecoImpl().delete();
            _Parameters.close();
            disposed = true;
            super.dispose(disposing);
        }
    }

    public com.microsoft.cognitiveservices.speech.internal.SpeechRecognizer getRecoImpl() {
        return recoImpl;
    }

    private com.microsoft.cognitiveservices.speech.internal.SpeechRecognizer recoImpl;
    private ResultHandlerImpl intermediateResultHandler;
    private ResultHandlerImpl finalResultHandler;
    private ErrorHandlerImpl errorHandler;
    private boolean disposed = false;

    // Defines an internal class to raise an event for intermediate/final result when a corresponding callback is invoked by the native layer.
    private class ResultHandlerImpl extends com.microsoft.cognitiveservices.speech.internal.SpeechRecognitionEventListener
    {
        ResultHandlerImpl(SpeechRecognizer recognizer, boolean isFinalResultHandler)
        {
            this.recognizer = recognizer;
            this.isFinalResultHandler = isFinalResultHandler;
        }

        @Override
        public void execute(com.microsoft.cognitiveservices.speech.internal.SpeechRecognitionEventArgs eventArgs)
        {
            if (recognizer.disposed)
            {
                return;
            }

            SpeechRecognitionResultEventArgs resultEventArg = new SpeechRecognitionResultEventArgs(eventArgs);
            EventHandlerImpl<SpeechRecognitionResultEventArgs> handler = isFinalResultHandler ? recognizer.FinalResultReceived : recognizer.IntermediateResultReceived;
            if (handler != null)
            {
                handler.fireEvent(this.recognizer, resultEventArg);
            }
        }

        private SpeechRecognizer recognizer;
        private boolean isFinalResultHandler;
    }

    // Defines an internal class to raise an event for error during recognition when a corresponding callback is invoked by the native layer.
    private class ErrorHandlerImpl extends com.microsoft.cognitiveservices.speech.internal.SpeechRecognitionEventListener
    {
        ErrorHandlerImpl(SpeechRecognizer recognizer)
        {
            this.recognizer = recognizer;
        }

        @Override
        public void execute(com.microsoft.cognitiveservices.speech.internal.SpeechRecognitionEventArgs eventArgs)
        {
            if (recognizer.disposed)
            {
                return;
            }

            RecognitionErrorEventArgs resultEventArg = new RecognitionErrorEventArgs(eventArgs.getSessionId(), eventArgs.getResult().getReason());
            EventHandlerImpl<RecognitionErrorEventArgs> handler = this.recognizer.RecognitionErrorRaised;

            if (handler != null)
            {
                handler.fireEvent(this.recognizer, resultEventArg);
            }
        }

        private SpeechRecognizer recognizer;
    }
}
