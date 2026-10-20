import groovy.json.JsonOutput
import org.gradle.api.GradleException

import java.nio.file.Files
import java.security.MessageDigest

/** Runs the production verifier against temporary resource trees; no test framework or Android SDK needed. */
class VerifyGraphicsResourcesRegression {
    static void main(String[] args) {
        Map<String, Closure> cases = [
            'unrelated engine shaders and sibling directories are accepted': { Fixture fixture ->
                fixture.imported('filters', 'registered.glsl')
                fixture.descriptor('filters/registered.glsl')
                fixture.asset('shaders/copy.f.glsl')
                fixture.asset('another_engine/effects/render.v.glsl')
                fixture.asset('filters_extra/unrelated.glsl')
                fixture.verify()
            },
            'generated contributions coexist with unrelated engine shaders': { Fixture fixture ->
                fixture.generated('generated/registered.glsl')
                fixture.descriptor('generated/registered.glsl')
                fixture.asset('shaders/render.f.glsl')
                fixture.verify()
            },
            'unregistered shader in an imported contribution is rejected': { Fixture fixture ->
                fixture.imported('filters', 'registered.glsl')
                fixture.asset('filters/unregistered.glsl')
                expectFailure('Unregistered graphics shaders:') { fixture.verify() }
            },
            'unregistered nested shader in an imported contribution is rejected': { Fixture fixture ->
                fixture.imported('filters', 'registered.glsl')
                fixture.asset('filters/nested/unregistered.glsl')
                expectFailure('Unregistered graphics shaders:') { fixture.verify() }
            },
            'unregistered shader in a generated contribution is rejected': { Fixture fixture ->
                fixture.generated('generated/registered.glsl')
                fixture.asset('generated/unregistered.glsl')
                expectFailure('Unregistered graphics shaders:') { fixture.verify() }
            },
            'unregistered nested shader in a generated contribution is rejected': { Fixture fixture ->
                fixture.generated('generated/registered.glsl')
                fixture.asset('generated/nested/unregistered.glsl')
                expectFailure('Unregistered graphics shaders:') { fixture.verify() }
            },
            'descriptor assets outside contribution ownership still require provenance': { Fixture fixture ->
                fixture.asset('shaders/unregistered.glsl')
                fixture.descriptor('shaders/unregistered.glsl')
                expectFailure('Unregistered graphics descriptor asset:') { fixture.verify() }
            },
            'missing descriptor assets are rejected': { Fixture fixture ->
                fixture.descriptor('missing/absent.glsl')
                expectFailure('missing or unsafe asset:') { fixture.verify() }
            },
            'unsafe descriptor paths are rejected': { Fixture fixture ->
                fixture.descriptor('../outside.glsl')
                expectFailure('invalid asset path:') { fixture.verify() }
            },
            'modified registered bytes are rejected': { Fixture fixture ->
                fixture.imported('filters', 'registered.glsl')
                fixture.asset('filters/registered.glsl', 'modified after registration')
                expectFailure('Graphics artifact SHA-256 mismatch:') { fixture.verify() }
            },
            'contribution ownership cannot escape assets': { Fixture fixture ->
                fixture.manifest([assetDirectory: '../outside', files: []])
                expectFailure('Invalid graphics asset directory:') { fixture.verify() }
            },
        ]
        cases.each { name, check ->
            File root = Files.createTempDirectory('graphics-resources-regression-').toFile()
            try {
                check.call(new Fixture(root))
                println('PASS: ' + name)
            } finally {
                root.deleteDir()
            }
        }
        println('Graphics resource regressions passed: ' + cases.size())
    }

    private static void expectFailure(String message, Closure action) {
        try {
            action.call()
        } catch (GradleException failure) {
            assert failure.message.contains(message): failure.message
            return
        }
        throw new AssertionError((Object) ('Expected verifier failure containing: ' + message))
    }

    private static final class Fixture {
        final File engine
        final File assets
        final File manifests
        final File descriptors

        Fixture(File root) {
            engine = new File(root, 'engine')
            assets = new File(engine, 'src/main/assets')
            manifests = new File(engine, 'patches')
            descriptors = new File(engine, 'src/main/graphicsfilters')
            [assets, manifests, descriptors].each { it.mkdirs() }
        }

        File asset(String path, String content = 'void main() {}') {
            File target = new File(assets, path)
            target.parentFile.mkdirs()
            target.setText(content, 'UTF-8')
            return target
        }

        void imported(String directory, String name) {
            File target = asset(directory + '/' + name)
            manifest([assetDirectory: directory, files: [[file: name, sha256: digest(target)]]])
        }

        void generated(String path) {
            File target = asset(path)
            manifest([generated: [[file: path, sha256: digest(target)]]])
        }

        void manifest(Map data) {
            new File(manifests, 'contribution.json').setText(JsonOutput.toJson(data), 'UTF-8')
        }

        void descriptor(String path) {
            new File(descriptors, 'filters.json').setText(
                    JsonOutput.toJson([filters: [[asset: path]]]), 'UTF-8')
        }

        void verify() {
            List<File> inputs = []
            assets.eachFileRecurse { file ->
                if (file.isFile() && (file.name.endsWith('.glsl') || file.name.startsWith('LICENSE'))) {
                    inputs.add(file)
                }
            }
            VerifyGraphicsResources.verifyResources(engine, manifests, descriptors, inputs)
        }

        private static String digest(File file) {
            MessageDigest.getInstance('SHA-256').digest(file.bytes).encodeHex().toString()
        }
    }
}
