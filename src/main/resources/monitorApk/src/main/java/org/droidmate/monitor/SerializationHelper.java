package org.droidmate.monitor;

import java.io.*;

class SerializationHelper {

	static void writeObjectToStream(DataOutputStream outputStream, Object toWrite) throws IOException {
		ObjectOutputStream objectOutput = new ObjectOutputStream(outputStream);
		objectOutput.writeObject(toWrite);
		objectOutput.flush();
	}

	static Object readObjectFromStream(DataInputStream inputStream) throws IOException, ClassNotFoundException {
		return new ObjectInputStream(inputStream).readObject();
	}
}