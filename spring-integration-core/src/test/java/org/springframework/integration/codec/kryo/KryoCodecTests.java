/*
 * Copyright 2015-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.integration.codec.kryo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.assertj.core.data.Offset;
import org.junit.Test;

import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.GenericMessage;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.KryoException;
import com.esotericsoftware.kryo.io.Output;

/**
 * @author David Turanski
 * @author Artem Bilan
 *
 * @since 4.2
 */
public class KryoCodecTests {

	@Test
	public void testStringSerialization() throws IOException {
		String str = "hello";
		PojoCodec codec = new PojoCodec();
		ByteArrayOutputStream bos = new ByteArrayOutputStream();

		codec.encode(str, bos);

		String s2 = codec.decode(bos.toByteArray(), String.class);
		assertThat(s2).isEqualTo(str);
	}

	@Test
	public void testSerializationWithStreams() throws IOException {
		String str = "hello";
		File file = new File("test.ser");
		PojoCodec codec = new PojoCodec();
		FileOutputStream fos = new FileOutputStream(file);
		codec.encode(str, fos);
		fos.close();

		FileInputStream fis = new FileInputStream(file);
		String s2 = codec.decode(fis, String.class);
		file.delete();
		assertThat(s2).isEqualTo(str);
	}

	@Test
	public void testPojoSerialization() throws IOException {
		PojoCodec codec = new PojoCodec(new KryoClassListRegistrar(SomeClassWithNoDefaultConstructors.class));
		SomeClassWithNoDefaultConstructors foo = new SomeClassWithNoDefaultConstructors("foo", 123);
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		codec.encode(foo, bos);
		Object foo2 = codec.decode(bos.toByteArray(), SomeClassWithNoDefaultConstructors.class);
		assertThat(foo2).isEqualTo(foo);
	}

	@Test
	public void testPrimitiveSerialization() throws IOException {
		PojoCodec codec = new PojoCodec();

		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		codec.encode(true, bos);
		boolean b = codec.decode(bos.toByteArray(), Boolean.class);
		assertThat(b).isEqualTo(true);
		b = codec.decode(bos.toByteArray(), boolean.class);
		assertThat(b).isEqualTo(true);

		bos = new ByteArrayOutputStream();
		codec.encode(3.14159, bos);

		double d = codec.decode(bos.toByteArray(), double.class);
		assertThat(d).isCloseTo(3.14159, Offset.offset(0.00001));

		bos = new ByteArrayOutputStream();
		codec.encode(3.14159, bos);

		d = codec.decode(bos.toByteArray(), Double.class);
		assertThat(d).isCloseTo(3.14159, Offset.offset(0.00001));

	}

	@Test
	public void testMapSerialization() throws IOException {
		PojoCodec codec = new PojoCodec(new KryoClassListRegistrar(HashMap.class));
		Map<String, Integer> map = new HashMap<>();
		map.put("one", 1);
		map.put("two", 2);
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		codec.encode(map, bos);
		Map<?, ?> m2 = (Map<?, ?>) codec.decode(bos.toByteArray(), HashMap.class);
		assertThat(m2.size()).isEqualTo(2);
		assertThat(m2.get("one")).isEqualTo(1);
		assertThat(m2.get("two")).isEqualTo(2);
	}

	@Test
	public void testComplexObjectSerialization() throws IOException {
		PojoCodec codec = new PojoCodec(new KryoClassListRegistrar(Foo.class));
		Foo foo = new Foo();
		foo.put("one", 1);
		foo.put("two", 2);
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		codec.encode(foo, bos);

		Foo foo2 = codec.decode(bos.toByteArray(), Foo.class);
		assertThat(foo2.get("one")).isEqualTo(1);
		assertThat(foo2.get("two")).isEqualTo(2);
	}

	@Test
	public void testUnregisteredTypeIsRejectedOnDecode() {
		byte[] bytes = encodeWithoutRegistrationRequired(null, new Gadget("touch /tmp/pwned"));
		PojoCodec codec = new PojoCodec();
		assertThatIllegalArgumentException()
				.isThrownBy(() -> codec.decode(bytes, Gadget.class))
				.withMessageContaining("Class is not registered: " + Gadget.class.getName());
	}

	@Test
	public void testUnregisteredTypeIsRejectedOnEncode() {
		PojoCodec codec = new PojoCodec();
		assertThatIllegalArgumentException()
				.isThrownBy(() -> codec.encode(new Gadget("touch /tmp/pwned")))
				.withMessageContaining("Class is not registered: " + Gadget.class.getName());
	}

	@Test
	public void testMessageSerialization() throws IOException {
		MessageCodec codec = new MessageCodec();
		Message<String> message = MessageBuilder.withPayload("foo")
				.setHeader("bar", "baz")
				.build();
		Message<?> decoded = codec.decode(codec.encode(message), GenericMessage.class);
		assertThat(decoded.getPayload()).isEqualTo("foo");
		assertThat(decoded.getHeaders().get("bar")).isEqualTo("baz");
	}

	@Test
	public void testMessageWithUnregisteredPayloadIsRejectedOnDecode() {
		byte[] bytes = encodeWithoutRegistrationRequired(new MessageKryoRegistrar(),
				new GenericMessage<>(new Gadget("touch /tmp/pwned")));
		MessageCodec codec = new MessageCodec();
		assertThatExceptionOfType(KryoException.class)
				.isThrownBy(() -> codec.decode(bytes, GenericMessage.class))
				.withMessageContaining("Class is not registered: " + Gadget.class.getName());
	}

	@Test
	public void testMessageWithUnregisteredHeaderValueIsRejectedOnDecode() {
		byte[] bytes = encodeWithoutRegistrationRequired(new MessageKryoRegistrar(),
				MessageBuilder.withPayload("foo")
						.setHeader("gadget", new Gadget("touch /tmp/pwned"))
						.build());
		MessageCodec codec = new MessageCodec();
		assertThatExceptionOfType(KryoException.class)
				.isThrownBy(() -> codec.decode(bytes, GenericMessage.class))
				.withMessageContaining("Class is not registered: " + Gadget.class.getName());
	}

	/**
	 * Simulate untrusted data: serialize with a {@link Kryo} instance which doesn't
	 * require class registration, so unregistered types are written by their class names.
	 */
	private static byte[] encodeWithoutRegistrationRequired(KryoRegistrar registrar, Object object) {
		Kryo kryo = new Kryo();
		kryo.setRegistrationRequired(false);
		if (registrar != null) {
			registrar.registerTypes(kryo);
		}
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		Output output = new Output(bos);
		kryo.writeObject(output, object);
		output.close();
		return bos.toByteArray();
	}

	static class Gadget {

		private String command;

		Gadget(String command) {
			this.command = command;
		}

		public String getCommand() {
			return this.command;
		}

	}

	static class SomeClassWithNoDefaultConstructors {

		private String val1;

		private int val2;

		SomeClassWithNoDefaultConstructors(String val1, int val2) {
			this.val1 = val1;
			this.val2 = val2;
		}

		@Override
		public boolean equals(Object other) {
			if (!(other instanceof SomeClassWithNoDefaultConstructors)) {
				return false;
			}
			SomeClassWithNoDefaultConstructors that = (SomeClassWithNoDefaultConstructors) other;
			return (this.val1.equals(that.val1) && this.val2 == that.val2);
		}

		@Override
		public int hashCode() {
			int result = this.val1.hashCode();
			result = 31 * result + this.val2;
			return result;
		}

	}

	static class Foo {

		private Map<Object, Object> map;

		Foo() {
			this.map = new HashMap<>();
		}

		public void put(Object key, Object value) {
			this.map.put(key, value);
		}

		public Object get(Object key) {
			return this.map.get(key);
		}

	}

}
