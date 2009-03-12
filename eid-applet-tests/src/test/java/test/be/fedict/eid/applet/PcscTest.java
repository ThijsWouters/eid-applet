/*
 * eID Applet Project.
 * Copyright (C) 2008-2009 FedICT.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License version
 * 3.0 as published by the Free Software Foundation.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, see 
 * http://www.gnu.org/licenses/.
 */

package test.be.fedict.eid.applet;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.awt.Component;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.security.Signature;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Locale;

import javax.imageio.ImageIO;
import javax.smartcardio.Card;
import javax.smartcardio.CardChannel;
import javax.smartcardio.CommandAPDU;
import javax.smartcardio.ResponseAPDU;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.Before;
import org.junit.Test;

import be.fedict.eid.applet.Messages;
import be.fedict.eid.applet.PcscEid;
import be.fedict.eid.applet.PcscEidSpi;
import be.fedict.eid.applet.Status;
import be.fedict.eid.applet.View;

/**
 * Integration tests for PC/SC eID component.
 * 
 * @author fcorneli
 * 
 */
public class PcscTest {

	private static final Log LOG = LogFactory.getLog(PcscTest.class);

	public static class TestView implements View {

		@Override
		public void addDetailMessage(String detailMessage) {
			LOG.debug("detail: " + detailMessage);
		}

		@Override
		public Component getParentComponent() {
			return null;
		}

		@Override
		public boolean privacyQuestion(boolean includeAddress,
				boolean includePhoto) {
			return false;
		}

		@Override
		public void setStatusMessage(Status status, String statusMessage) {
			LOG.debug("status: [" + status + "]: " + statusMessage);
		}

		@Override
		public void progressIndication(int max, int current) {
		}
	}

	private Messages messages;

	@Before
	public void setUp() {
		this.messages = new Messages(Locale.getDefault());
	}

	@Test
	public void pcscAuthnSignature() throws Exception {
		PcscEidSpi pcscEidSpi = new PcscEid(new TestView(), this.messages);
		if (false == pcscEidSpi.isEidPresent()) {
			LOG.debug("insert eID card");
			pcscEidSpi.waitForEidPresent();
		}
		byte[] challenge = "hello world".getBytes();
		byte[] signatureValue = pcscEidSpi.signAuthn(challenge);
		List<X509Certificate> authnCertChain = pcscEidSpi
				.getAuthnCertificateChain();
		pcscEidSpi.close();

		Signature signature = Signature.getInstance("SHA1withRSA");
		signature.initVerify(authnCertChain.get(0).getPublicKey());
		signature.update(challenge);
		boolean result = signature.verify(signatureValue);
		assertTrue(result);
	}

	@Test
	public void logoff() throws Exception {
		PcscEidSpi pcscEidSpi = new PcscEid(new TestView(), this.messages);
		if (false == pcscEidSpi.isEidPresent()) {
			LOG.debug("insert eID card");
			pcscEidSpi.waitForEidPresent();
		}

		pcscEidSpi.logoff();

		pcscEidSpi.close();
	}

	@Test
	public void pcscChangePin() throws Exception {
		PcscEidSpi pcscEidSpi = new PcscEid(new TestView(), this.messages);
		if (false == pcscEidSpi.isEidPresent()) {
			LOG.debug("insert eID card");
			pcscEidSpi.waitForEidPresent();
		}

		pcscEidSpi.changePin();

		pcscEidSpi.close();
	}

	@Test
	public void pcscUnblockPin() throws Exception {
		PcscEidSpi pcscEidSpi = new PcscEid(new TestView(), this.messages);
		if (false == pcscEidSpi.isEidPresent()) {
			LOG.debug("insert eID card");
			pcscEidSpi.waitForEidPresent();
		}

		pcscEidSpi.unblockPin();

		pcscEidSpi.close();
	}

	@Test
	public void photo() throws Exception {
		PcscEidSpi pcscEidSpi = new PcscEid(new TestView(), this.messages);
		if (false == pcscEidSpi.isEidPresent()) {
			LOG.debug("insert eID card");
			pcscEidSpi.waitForEidPresent();
		}

		byte[] photo = pcscEidSpi.readFile(PcscEid.PHOTO_FILE_ID);
		LOG.debug("image size: " + photo.length);
		BufferedImage image = ImageIO.read(new ByteArrayInputStream(photo));
		assertNotNull(image);
		LOG.debug("width: " + image.getWidth());
		LOG.debug("height: " + image.getHeight());

		pcscEidSpi.close();
	}

	@Test
	public void testCcid() throws Exception {
		PcscEid pcscEid = new PcscEid(new TestView(), this.messages);
		if (false == pcscEid.isEidPresent()) {
			LOG.debug("insert eID card");
			pcscEid.waitForEidPresent();
		}

		Card card = pcscEid.getCard();
		// GET FEATURE LIST
		byte[] features = card.transmitControlCommand(0x42000D48, new byte[0]);
		if (0 == features.length) {
			LOG.debug("no CCID reader");
			return;
		}
		LOG.debug("feature list: " + new String(Hex.encodeHex(features)));
		LOG.debug("feature verify pin direct: "
				+ hasFeature(FEATURE_VERIFY_PIN_DIRECT_TAG, features));
		Integer verifyPinControl = findFeature(FEATURE_VERIFY_PIN_DIRECT_TAG,
				features);
		LOG.debug("VERIFY PIN control: "
				+ Integer.toHexString(verifyPinControl));

		CardChannel cardChannel = pcscEid.getCardChannel();
		CommandAPDU setApdu = new CommandAPDU(0x00, 0x22, 0x41, 0xB6,
				new byte[] { 0x04, // length of following data
						(byte) 0x80, // algo ref
						0x01, // rsa pkcs#1
						(byte) 0x84, // tag for private key ref
						(byte) 0x82 });
		ResponseAPDU responseApdu = cardChannel.transmit(setApdu);
		if (0x9000 != responseApdu.getSW()) {
			throw new RuntimeException("SELECT error");
		}

		ByteArrayOutputStream verifyCommand = new ByteArrayOutputStream();
		verifyCommand.write(30); // bTimeOut
		verifyCommand.write(30); // bTimeOut2
		verifyCommand.write(0x89); // bmFormatString: BCD PIN - SPR532 only,
		// else 0x01
		verifyCommand.write(0x47); // bmPINBlockString
		verifyCommand.write(0x04); // bmPINLengthFormat
		verifyCommand.write(new byte[] { 0x0C, 0x04 }); // wPINMaxExtraDigit
		verifyCommand.write(0x02); // bEntryValidationCondition
		verifyCommand.write(0x01); // bNumberMessage
		verifyCommand.write(new byte[] { 0x13, 0x08 }); // wLangId
		verifyCommand.write(0x00); // bMsgIndex
		verifyCommand.write(new byte[] { 0x00, 0x00, 0x00 }); // bTeoPrologue
		byte[] verifyApdu = new byte[] { 0x00, 0x20, 0x00, 0x01, 0x08, 0x20,
				(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
				(byte) 0xFF, (byte) 0xFF, (byte) 0xFF };
		verifyCommand.write(verifyApdu.length & 0xff); // ulDataLength[0]
		verifyCommand.write(0x00); // ulDataLength[1]
		verifyCommand.write(0x00); // ulDataLength[2]
		verifyCommand.write(0x00); // ulDataLength[3]
		verifyCommand.write(verifyApdu); // abData

		card.transmitControlCommand(verifyPinControl, verifyCommand
				.toByteArray());
	}

	public static final byte FEATURE_VERIFY_PIN_DIRECT_TAG = 0x07;

	private boolean hasFeature(byte featureTag, byte[] features) {
		int idx = 0;
		while (idx < features.length) {
			byte tag = features[idx];
			if (featureTag == tag) {
				return true;
			}
			idx += 1 + 1 + 4;
		}
		return false;
	}

	private Integer findFeature(byte featureTag, byte[] features) {
		int idx = 0;
		while (idx < features.length) {
			byte tag = features[idx];
			idx++;
			idx++;
			if (featureTag == tag) {
				int feature = 0;
				for (int count = 0; count < 3; count++) {
					feature |= features[idx] & 0xff;
					idx++;
					feature <<= 8;
				}
				feature |= features[idx] & 0xff;
				return feature;
			}
			idx += 4;
		}
		return null;
	}

	@Test
	public void testListReaders() throws Exception {
		PcscEid pcscEid = new PcscEid(new TestView(), this.messages);
		LOG.debug("reader list: " + pcscEid.getReaderList());
	}
}
