class CCBackground extends HTMLElement{
	constructor(){
		super();
		const root = this.attachShadow({mode:'open'});
		const src = this.getAttribute('src') || '';
		const alt = this.getAttribute('alt') || 'Background';
		root.innerHTML = `
			<style>
				:host{
					position: absolute;
					inset: 0;
					z-index: 0;
					display:block;
					overflow: hidden;
				}
				.frame{ position:absolute; inset:0; }
				img{
					display:block;
					width:100%;
					height:100%;
					object-fit: cover;
					object-position: center;
					transform: translateZ(0);
					backface-visibility: hidden;
				}
			</style>
			<div class="frame" role="img" aria-label="${alt}">
				<img src="${src}" alt="${alt}">
			</div>
		`;
	}
}
customElements.define('cc-background', CCBackground);