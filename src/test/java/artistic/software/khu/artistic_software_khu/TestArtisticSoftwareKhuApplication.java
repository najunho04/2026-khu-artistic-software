package artistic.software.khu.artistic_software_khu;

import org.springframework.boot.SpringApplication;

public class TestArtisticSoftwareKhuApplication {

	public static void main(String[] args) {
		SpringApplication.from(ArtisticSoftwareKhuApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
