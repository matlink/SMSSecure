package org.thoughtcrime.securesms.transport;

import android.content.Context;
import android.util.Log;
import android.util.Pair;

import org.thoughtcrime.securesms.crypto.IdentityKeyUtil;
import org.thoughtcrime.securesms.crypto.KeyExchangeProcessor;
import org.thoughtcrime.securesms.database.model.SmsMessageRecord;
import org.thoughtcrime.securesms.mms.PartParser;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.sms.RawTransportDetails;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.textsecure.crypto.IdentityKey;
import org.whispersystems.textsecure.crypto.IdentityKeyPair;
import org.whispersystems.textsecure.crypto.KeyUtil;
import org.whispersystems.textsecure.crypto.MasterSecret;
import org.whispersystems.textsecure.crypto.MessageCipher;
import org.whispersystems.textsecure.crypto.protocol.PreKeyBundleMessage;
import org.whispersystems.textsecure.push.PreKeyEntity;
import org.whispersystems.textsecure.push.PushAttachmentData;
import org.whispersystems.textsecure.push.PushServiceSocket;
import org.whispersystems.textsecure.push.PushTransportDetails;
import org.whispersystems.textsecure.push.RateLimitException;
import org.whispersystems.textsecure.util.PhoneNumberFormatter;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import ws.com.google.android.mms.ContentType;
import ws.com.google.android.mms.pdu.PduBody;
import ws.com.google.android.mms.pdu.SendReq;

public class PushTransport extends BaseTransport {

  public static final int TYPE_MESSAGE_PLAINTEXT     = 0;
  public static final int TYPE_MESSAGE_CIPHERTEXT    = 1;
  public static final int TYPE_MESSAGE_KEY_EXCHANGE  = 2;
  public static final int TYPE_MESSAGE_PREKEY_BUNDLE = 3;

  private final Context      context;
  private final MasterSecret masterSecret;

  public PushTransport(Context context, MasterSecret masterSecret) {
    this.context      = context.getApplicationContext();
    this.masterSecret = masterSecret;
  }

  public void deliver(SmsMessageRecord message) throws IOException {
    try {
      String            localNumber = TextSecurePreferences.getLocalNumber(context);
      String            password    = TextSecurePreferences.getPushServerPassword(context);
      PushServiceSocket socket      = new PushServiceSocket(context, localNumber, password);

      Recipient recipient                = message.getIndividualRecipient();
      String    plaintext                = message.getBody().getBody();
      String    recipientCanonicalNumber = PhoneNumberFormatter.formatNumber(recipient.getNumber(),
                                                                             localNumber);

      Pair<Integer, String> typeAndCiphertext = getEncryptedMessage(socket, recipient, recipientCanonicalNumber, plaintext);

      socket.sendMessage(recipientCanonicalNumber, typeAndCiphertext.second, typeAndCiphertext.first);

      context.sendBroadcast(constructSentIntent(context, message.getId(), message.getType()));
    } catch (RateLimitException e) {
      Log.w("PushTransport", e);
      throw new IOException("Rate limit exceeded.");
    }
  }

  public void deliver(SendReq message, List<String> destinations) throws IOException {
    try {
      String                   localNumber = TextSecurePreferences.getLocalNumber(context);
      String                   password    = TextSecurePreferences.getPushServerPassword(context);
      PushServiceSocket        socket      = new PushServiceSocket(context, localNumber, password);
      String                   messageText = PartParser.getMessageText(message.getBody());
      List<PushAttachmentData> attachments = getAttachmentsFromBody(message.getBody());

      if (attachments.isEmpty()) socket.sendMessage(destinations, messageText, TYPE_MESSAGE_PLAINTEXT);
      else                       socket.sendMessage(destinations, messageText, attachments, TYPE_MESSAGE_PLAINTEXT);
    } catch (RateLimitException e) {
      Log.w("PushTransport", e);
      throw new IOException("Rate limit exceeded.");
    }
  }

  private List<PushAttachmentData> getAttachmentsFromBody(PduBody body) {
    List<PushAttachmentData> attachments = new LinkedList<PushAttachmentData>();

    for (int i=0;i<body.getPartsNum();i++) {
      String contentType = Util.toIsoString(body.getPart(i).getContentType());

      if (ContentType.isImageType(contentType) ||
          ContentType.isAudioType(contentType) ||
          ContentType.isVideoType(contentType))
      {
        attachments.add(new PushAttachmentData(contentType, body.getPart(i).getData()));
      }
    }

    return attachments;
  }

  private Pair<Integer, String> getEncryptedMessage(PushServiceSocket socket, Recipient recipient,
                                                    String canonicalRecipientNumber, String plaintext)
      throws IOException
  {
    if (KeyUtil.isNonPrekeySessionFor(context, masterSecret, recipient)) {
      Log.w("PushTransport", "Sending standard ciphertext message...");
      String ciphertext = getEncryptedMessageForExistingSession(recipient, plaintext);
      return new Pair<Integer, String>(TYPE_MESSAGE_CIPHERTEXT, ciphertext);
    } else if (KeyUtil.isSessionFor(context, recipient)) {
      Log.w("PushTransport", "Sending prekeybundle ciphertext message for existing session...");
      String ciphertext = getEncryptedPrekeyBundleMessageForExistingSession(recipient, plaintext);
      return new Pair<Integer, String>(TYPE_MESSAGE_PREKEY_BUNDLE, ciphertext);
    } else {
      Log.w("PushTransport", "Sending prekeybundle ciphertext message for new session...");
      String ciphertext = getEncryptedPrekeyBundleMessageForNewSession(socket, recipient, canonicalRecipientNumber, plaintext);
      return new Pair<Integer, String>(TYPE_MESSAGE_PREKEY_BUNDLE, ciphertext);
    }
  }

  private String getEncryptedPrekeyBundleMessageForExistingSession(Recipient recipient,
                                                                   String plaintext)
  {
    IdentityKeyPair identityKeyPair = IdentityKeyUtil.getIdentityKeyPair(context, masterSecret);
    IdentityKey     identityKey     = identityKeyPair.getPublicKey();

    MessageCipher message = new MessageCipher(context, masterSecret, identityKeyPair, new RawTransportDetails());
    byte[] bundledMessage = message.encrypt(recipient, plaintext.getBytes());

    PreKeyBundleMessage preKeyBundleMessage = new PreKeyBundleMessage(identityKey, bundledMessage);
    return preKeyBundleMessage.serialize();
  }

  private String getEncryptedPrekeyBundleMessageForNewSession(PushServiceSocket socket,
                                                              Recipient recipient,
                                                              String canonicalRecipientNumber,
                                                              String plaintext)
      throws IOException
  {
    IdentityKeyPair      identityKeyPair = IdentityKeyUtil.getIdentityKeyPair(context, masterSecret);
    IdentityKey          identityKey     = identityKeyPair.getPublicKey();
    PreKeyEntity         preKey          = socket.getPreKey(canonicalRecipientNumber);
    KeyExchangeProcessor processor       = new KeyExchangeProcessor(context, masterSecret, recipient);

    processor.processKeyExchangeMessage(preKey);

    MessageCipher message = new MessageCipher(context, masterSecret, identityKeyPair, new RawTransportDetails());
    byte[] bundledMessage = message.encrypt(recipient, plaintext.getBytes());

    PreKeyBundleMessage preKeyBundleMessage = new PreKeyBundleMessage(identityKey, bundledMessage);
    return preKeyBundleMessage.serialize();
  }

  private String getEncryptedMessageForExistingSession(Recipient recipient, String plaintext)
      throws IOException
  {
    IdentityKeyPair identityKeyPair = IdentityKeyUtil.getIdentityKeyPair(context, masterSecret);
    MessageCipher   messageCipher   = new MessageCipher(context, masterSecret, identityKeyPair,
                                                        new PushTransportDetails());

    return new String(messageCipher.encrypt(recipient, plaintext.getBytes()));
  }

}